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


## License

This project is licensed under the Apache License 2.0.

NOTICE: This project uses components from the OpenJDK project, which is licensed under
the GNU General Public License v2 with the Classpath Exception.
See: https://openjdk.org/legal/gplv2+ce.html

No code from OpenJDK is modified or included in source form in this project.
During the build process, OpenJDK source code may be downloaded and compiled into bytecode
for inclusion in the final WebAssembly output, as permitted by the Classpath Exception.