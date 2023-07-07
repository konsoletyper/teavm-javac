An offline Java compiler that runs in the browser.

This is merely javac from OpenJDK compiled with [TeaVM](https://teavm.org).


## Running example locally

```
./gradlew :ui:appRunWar
```

## Building

```
./gradlew :ui:war
```

Resulting `.war` file can be found in `ui/build/libs`. 


## Roadmap

* Document compiler library API
* Run TeaVM tests against this compiler
* Java parsing/AST attribution API
* Semantic highlighting and autocompletion
