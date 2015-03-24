package com.rmazur.oak;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command line interface for Oak.
 * Prints Oak JSON to system output.
 */
public final class Cmd {

  @Parameter(names = {"--classpath", "-cp"}, required = true, description = "Classpath to analyze")
  private String classpath;

  @Parameter(names = {"--exclude", "-e"}, description = "Name pattern to exclude")
  private List<String> exclude;

  @Parameter(names = {"--terminal", "-t"}, description = "Name pattern to mark as a terminal node")
  private List<String> terminal;

  @Parameter(names = {"--format", "-f"}, description = "Output format (json or html)")
  private OutputFormat format = OutputFormat.JSON;

  @Parameter(names = {"--output", "-o"}, description = "Output directory for html")
  private String outputDir;

  private Cmd() {
    // Nothing.
  }

  public static void main(String[] args) {
    Cmd cmd = new Cmd();
    JCommander jc = new JCommander(cmd);
    try {
      jc.parse(args);
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      jc.usage();
      System.exit(1);
    }

    Oak.Builder oak = new Oak.Builder();
    if (cmd.exclude != null && !cmd.exclude.isEmpty()) {
      oak.exclude(cmd.exclude);
    }
    if (cmd.terminal != null && !cmd.terminal.isEmpty()) {
      oak.terminal(cmd.terminal);
    }
    List<File> classpath = Arrays.asList(cmd.classpath.split(File.pathSeparator)).stream()
        .map(File::new)
        .collect(Collectors.toList());
    if (cmd.format == OutputFormat.JSON) {
      oak.build().plant(classpath, System.out);
    } else {
      oak.build().plantHtml(classpath, new File(cmd.outputDir));
    }
  }

  private static enum OutputFormat {
    JSON,
    HTML
  }

}
