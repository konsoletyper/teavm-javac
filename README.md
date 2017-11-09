An offline Java compiler that runs in the browser.

This is merely javac from OpenJDK compiled with [TeaVM](http://teavm.org).

See live example [here](http://teavm.org/sandbox/).

**Warning**: this project is in its very early stage.

## Building

First, clone and build latest TeaVM:

```
$ git clone https://github.com/konsoletyper/teavm.git
$ install -DskipTests -Dteavm.build.all=false
```

Then, build this project using TeaVM:

```
mvn package
```

TeaVM will generate many error messages and produce a JavaScript file.
Ignore these messages and open `index.html`.
You should be able to compile simple Java sources and get bytecode (`Hello.class` file).
Currently, all diagnostics are printed to browser console. 