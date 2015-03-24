Oak
===

A tool that parses bytecode in the classpath and builds a graph of dependencies between classes.
Just for fun! *Requires Java 8.*

![Image example](sample.png)

Inspired by [**PaulTaykalo's**](https://github.com/PaulTaykalo) [objc-dependency-visualizer](https://github.com/PaulTaykalo/objc-dependency-visualizer).
And many thanks to an awesome [d3js](http://d3js.org/) library!

How to use
----------

**Manual way**

```bash
curl https://github.com/roman-mazur/oak/releases/download/v0.1.0/oak-cli.jar > oak-cli.jar
java -jar oak-cli.jar -cp path/to/your.jar:and/or/library.jar:or/classes/dir -f html -o deps-report
open deps-report/index.html
```

**Gradle plugin**
TODO
