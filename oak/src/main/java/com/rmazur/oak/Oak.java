package com.rmazur.oak;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.ClassReader.*;
import static org.objectweb.asm.Type.getType;

public final class Oak {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private static final Set<String> PRIMITIVES = new HashSet<>(Arrays.asList(
      "int", "long", "double", "float", "char", "byte", "boolean", "short"
  ));

  private final Set<Pattern> exclude;
  private final Set<Pattern> terminal;

  private Oak(final Set<Pattern> exclude, final Set<Pattern> terminal) {
    this.exclude = exclude;
    this.terminal = terminal;
  }

  private boolean included(String name) {
    return !name.startsWith("java.")
        && !name.startsWith("javax.")
        && !PRIMITIVES.contains(name)
        && !exclude.stream().anyMatch(p -> p.matcher(name).matches());
  }

  private boolean notTerminal(String name) {
    return !terminal.stream().anyMatch(p -> p.matcher(name).matches());
  }

  public void plant(Collection<File> classpath, Appendable out) {
    List<Leave> tree = classpath.stream()
        // Read byte code in classpath.
        .flatMap(f -> f.isDirectory()
            // Read .class files in the directory.
            ? classFiles(f).map(Oak::classNode)
            // Read jar entries.
            : classesInJar(f))
        // Filter classes.
        .filter(classNode -> {
          String name = javaTypeName(classNode.name);
          return included(name) && notTerminal(name);
        })
        // Get dependencies.
        .flatMap(classNode -> {
          // Asm API.
          @SuppressWarnings("unchecked")
          Stream<FieldNode> fieldsStream = classNode.fields.stream();
          @SuppressWarnings("unchecked")
          Stream<MethodNode> methodsStream = classNode.methods.stream();

          // Collect field dependencies.
          Set<String> dependnecies = fieldsStream.map(Oak::fieldType).filter(this::included).collect(Collectors.toSet());

          // Collect method dependencies.
          methodsStream.forEach(method -> {
            String mType = methodType(method);
            if (mType != null && included(mType)) {
              dependnecies.add(mType);
            }
            @SuppressWarnings("unchecked")
            ListIterator<AbstractInsnNode> insIterator = method.instructions.iterator();
            while (insIterator.hasNext()) {
              String depType = null;
              AbstractInsnNode insNode = insIterator.next();
              switch (insNode.getType()) {
                case AbstractInsnNode.METHOD_INSN:
                  depType = javaTypeName(((MethodInsnNode) insNode).owner);
                  break;
                case AbstractInsnNode.FIELD_INSN:
                  depType = javaTypeName(((FieldInsnNode) insNode).owner);
                  break;
                case AbstractInsnNode.LDC_INSN:
                  LdcInsnNode node = (LdcInsnNode) insNode;
                  if (node.cst instanceof Type) {
                    depType = javaTypeName(((Type) node.cst).getInternalName());
                  }
                  break;
                default:
              }
              if (depType != null && included(depType)) {
                dependnecies.add(depType);
              }
            }
          });

          // Add super class.
          String superDep = javaTypeName(classNode.superName);
          if (included(superDep)) {
            dependnecies.add(superDep);
          }
          // Add interfaces.
          @SuppressWarnings("unchecked")
          List<String> interfaceNames = classNode.interfaces;
          interfaceNames.stream().map(Oak::javaTypeName).filter(this::included).forEach(dependnecies::add);

          String source = javaTypeName(classNode.name);
          return dependnecies.stream()
              .filter(dep -> !dep.isEmpty() && !dep.equals(source))
              .map(dep -> new Leave(source, dep));
        }).collect(Collectors.toList());

    GSON.toJson(tree, out);
  }

  public void plantHtml(Collection<File> classpath, File outputDir) {
    Html.produceHtml(outputDir);
    Writer dataOut = Html.startDataWrite(outputDir);
    try {
      plant(classpath, dataOut);
    } finally {
      Html.finishDataWrite(dataOut);
    }
  }

  private static Stream<File> classFiles(File dir) {
    File[] files = dir.listFiles();
    if (files == null) {
      return Stream.empty();
    }
    Stream<File> filesStream = Arrays.asList(files).stream();
    return Stream.concat(
        filesStream.filter(f -> !f.isDirectory() && f.getName().endsWith(".class")),
        filesStream.filter(File::isDirectory).flatMap(Oak::classFiles)
    );
  }

  private static ClassNode classNode(File file) {
    try (InputStream in = new FileInputStream(file)) {
      return readByteCode(in);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Stream<ClassNode> classesInJar(File jar) {
    try (JarFile jf = new JarFile(jar)) {
      Collection<JarEntry> entries = jf.stream()
          .filter(e -> e.getName().endsWith(".class"))
          .collect(Collectors.toList());
      return entries.stream().map(e -> classNode(jf, e))
          // Read everything now, while jar file is open.
          .collect(Collectors.toList())
          .stream();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static ClassNode classNode(JarFile jar, JarEntry entry) {
    try (InputStream in = jar.getInputStream(entry)) {
      return readByteCode(in);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static ClassNode readByteCode(InputStream in) throws IOException {
    ClassReader reader = new ClassReader(in);
    ClassNode node = new ClassNode();
    reader.accept(node, SKIP_DEBUG | SKIP_FRAMES);
    return node;
  }

  private static String javaTypeName(String internalName) {
    return strip(internalName.replace('/', '.'));
  }

  private static String fieldType(FieldNode f) {
    return strip(getType(f.desc).getClassName());
  }
  private static String strip(String type) {
    // Remove array.
    String res = type;
    while (res.endsWith("[]")) {
      res = res.substring(0, res.length() - 2);
    }
    // Remove anonymous.
    res = res.replaceAll("\\$\\d+", "").replaceAll("\\[\\w", "").replaceAll(";", "");
    return res;
  }
  private static String methodType(MethodNode m) {
    String className = getType(m.desc).getClassName();
    return className != null ? strip(className) : null;
  }

  private static final class Leave {
    @SerializedName("source")
    final String source;
    @SerializedName("dest")
    final String dest;

    Leave(String source, String dest) {
      this.source = source;
      this.dest = dest;
    }
  }

  public static class Builder {

    private HashSet<Pattern> excludes;
    private HashSet<Pattern> terminals;

    public Builder excludePatterns(Collection<Pattern> p) {
      if (excludes == null) {
        excludes = new HashSet<>();
      }
      excludes.addAll(p);
      return this;
    }

    public Builder terminalPatterns(Collection<Pattern> p) {
      if (terminals == null) {
        terminals = new HashSet<>();
      }
      terminals.addAll(p);
      return this;
    }

    public Builder exclude(Collection<String> p) {
      return excludePatterns(p.stream().map(Pattern::compile).collect(Collectors.toList()));
    }

    public Builder exclude(String... p) {
      return exclude(Arrays.asList(p));
    }

    public Builder terminal(Collection<String> t) {
      return terminalPatterns(t.stream().map(Pattern::compile).collect(Collectors.toList()));
    }

    public Builder terminal(String... p) {
      return terminal(Arrays.asList(p));
    }

    public Oak build() {
      return new Oak(
          excludes == null ? Collections.emptySet() : excludes,
          terminals == null ? Collections.emptySet() : terminals
      );
    }
  }

}
