An offline Java compiler that runs in the browser.

This is merely javac from OpenJDK compiled with [TeaVM](http://teavm.org).

See live example [here](http://teavm.org/sandbox/).

## Building

```
$ mvn package
```

Resulting `.war` file can be found in `compiler/target/`.
Deploy it on Tomcat, or simply unpack all resources and serve them via HTTP. 