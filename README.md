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
$ mvn package
```

Resulting `.war` file can be found in `compiler/target/`.
Deploy it on Tomcat, or simply unpack all resources and serve them via HTTP. 