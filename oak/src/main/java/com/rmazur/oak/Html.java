package com.rmazur.oak;

import okio.BufferedSink;
import okio.Okio;
import okio.Sink;
import okio.Source;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

final class Html {

  private static String[] RESOURCES = {
      "index.html", "js/oak.js", "js/d3.v3.js", "js/d3js-LICENSE",
      "dependency.css"
  };

  private Html() { }

  private static void close(Closeable cls) {
    if (cls != null) {
      try {
        cls.close();
      } catch (IOException ignored) {
        // Ignore.
      }
    }
  }

  static Writer startDataWrite(File outputDir) {
    File dataFile = new File(outputDir, "data.js");
    try {
      FileWriter writer = new FileWriter(dataFile);
      writer.write("var dependencies = ");
      return writer;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static void finishDataWrite(Writer writer) {
    try {
      writer.write(";");
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      close(writer);
    }
  }

  static void produceHtml(File outputDir) {
    Arrays.asList(RESOURCES).stream()
        .forEach(resource -> {
          File dst = new File(outputDir, resource);
          dst.getParentFile().mkdirs();
          Source source = Okio.buffer(Okio.source(Html.class.getResourceAsStream("/html/".concat(resource))));
          BufferedSink sink = null;
          try {
            sink = Okio.buffer(Okio.sink(dst));
            sink.writeAll(source);
          } catch (IOException e) {
            throw new RuntimeException(e);
          } finally {
            close(sink);
            close(source);
          }
        });
  }

}
