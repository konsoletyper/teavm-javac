An offline Java compiler that runs in the browser.

This is two compilers in one WebAssembly module:

* Java compiler from OpenJDK
* [TeaVM](https://teavm.org).

Both are compiled into JAR with TeaVM.

See in action: https://teavm.org/playground.html


## Running example locally

```
./gradlew :ui:appRunWar
```

## Building

```
./gradlew :ui:war
```

Resulting `.war` file can be found in `ui/build/libs`.


## Using as a library

The latest WebAssembly module can be found here: https://teavm.org/playground/compiler.wasm

You should load it with TeaVM WebAssembly runtime. For example:

```js
import { load } from "./compiler.wasm-runtime.js";

let teavm = await load("./compiler.wasm");
let compilerLib = teavm.exports;
```

where compilerLib is `CompilerLibrary` object defined as follows:

```ts
declare interface CompilerLibrary {
    createCompiler(): Compiler
    installWorker()
} 
```

where `installWorker` is a convenience function that installs simple worker protocol which is described below.

`Compiler` is defined as follows:

```ts
declare class Compiler {
    addSourceFile(path: string, content: string)
    
    clearSourceFiles()
    
    // This can be not only `.class` file, but any file, e.g. some resources
    addClassFile(path: string, content: Int8Array)
    
    // Content is supposed to be a zip archive containing number of class files
    // It's equivalent for unpacking files from archive and passing each 
    // file to `addClassFile`
    addJarFile(content: Int8Array)
    
    clearInputClassFiles()
    
    // Set archive that includes definitions of standard Java library,
    // necessary for javac. This archive is generated with special tool,
    // the latest version can be found here: 
    // https://teavm.org/playground/compile-classlib-teavm.bin
    setSdk(content: Int8Array)

    // Set archive that includes implementation of standard Java library,
    // necessary for TEaVM. This archive is generated with special tool,
    // the latest version can be found here: 
    // https://teavm.org/playground/runtime-classlib-teavm.bin
    setTeaVMClasslib(content: Int8Array)

    onDiagnostic(listener: (Diagnostic) => void): ListenerRegistration;

    // Takes given source files and given input binary class files as dependencies.
    // 
    // Returns `true` if compilation was successful.
    // During execution may call listeners, passed to `onDiagnostic` method
    // when compiler finds any error in input files.
    compile(): boolean
    
    // Returns list of class files, produced by Java compiler
    listOutputFiles: string[]

    // Gets file, produced by Java compiler or 'null', if none found with given name
    getOutputFile(name: string): Int8Array

    // Gets all files, produced by Java compiler, as a zip archive
    getOutputJar(): Int8Array
    
    // Add class file to output files.
    // This can be useful when using this library only to produce WebAssembly 
    // from existing class files
    addOutputClassFile(path: string, content: Int8Array)

    // Content is supposed to be a zip archive containing number of class files
    // It's equivalent for unpacking files from archive and passing each 
    // file to `addOutputClassFile`
    addOutputJarFile(content: Int8Array)

    clearOutputFiles(): Int8Array

    // Finds classes that contain valid `main` method among output class files.
    detectMainClasses(): string[]

    // Takes given output class files (either produced by calling `compile` 
    // or written manually).
    // 
    // Returns `true` if compilation was successful.
    // During execution may call listeners, passed to `onDiagnostic` method
    // when compiler finds any error in input files.
    generateWebAssembly(options: {
        outputName: string, // base name for WebAssembly module
        mainClass: string, 
    }): boolean

    listWebAssemblyOutputFiles(): string[]
    getWebAssemblyOutputFile(path: string): Int8Array
    
    // Gets WebAssembly output files as a zip archive
    getWebAssemblyOutputArchive(): Int8Array
}
```

where 

```ts
declare class ListenerRegistration {
    destroy()
}

declare class Diagnostic {
    type: "javac" | "teavm"
    severity: "error" | "warning" | "other"
    fileName: string
    lineNumber: number
    message: string
}
declare class JavaDiagnostic extends Diagnostic {
    type: "javac"
    columnNumber: number
    startPosition: number
    position: number
    endPosition: number
}
declare class TeaVMDiagnostic extends Diagnostic {
    type: "teavm"
}
```

Please note that methods, that are supposed to add a file, overwrite existing files.

simple example:

```ts
let response = await fetch("https://teavm.org/playground/compile-classlib-teavm.bin");
compiler.setSdk(new Int8Array(await response.arrayBuffer()));

response = await fetch("https://teavm.org/playground/compile-classlib-teavm.bin");
compiler.setTeaVMClasslib(new Int8Array(await response.arrayBuffer()));

compiler.onDiagnostic(diagnostic => {
    console.log(diagnostic.type, diagnostic.severity, diagnostic.fileName, diagnostic.lineNumber,
        diagnostic.message);
});

compiler.addSourceFile("Main.java", HELLO_WORLD_JAVA_CODE);
compiler.compile();
compiler.generateWebAssembly({
    outputName: "app",
    mainClass: "Main"
});
let generatedWasm = compiler.getWebAssemblyOutputFile("app.wasm");

let outputTeaVM = await load(generatedWasm);
outputTeaVM.exports.main([]);
```

In a more complex scenario, you can re-use existing compiler instance without passing SDK and classlib again.
This should also make repeated compilation faster, since compilers can re-use results of previous builds.


### Using simple worker

When the worker initializes, it sends the following message to the page:

```js
{
    command: "initialized"
}
```

When you send a message to the worker, you should pass additional `id` property,
worker will tag its responses to given request with the same `id`.

Available requests:

```js
{
    command: "load-classlib",
    url: "URL of Java class library for javac",
    runtimeUrl: "URL of Java class library for TeaVM"
}
```

which is responded with

```js
{
    command: "ok"
}
```

upon completion, and 

```js
{
    command: "compile",
    text: "text of Main.java"
}
```

which is responded with:

```js
{
    command: "compilation-complete",
    status: "successful" | "errors"
    script: result /* Int8Array, containing WebAssembly module, if successful */
}
```

Additionally, worker sends the following messages during compilation:

```js
{
    command: "compiler-diagnostic" | "diagnostic",
    severity: "error" | "warning" | "other",
    fileName: string,
    lineNumber: number,
    // etc, see JavaDiagnostic and TeaVMDiagnostic
}
```

where `compiler-diagnostic` stands for "Java compiler diagnostic" and `diagnostic` stands for
"TeaVM diagnostic"


### Building library from sources

You need Java 21 installed on your machine.

Run

```
./gradlew :compiler:build
```

Library can be found at `compiler/build/distributions/dist.zip`.


## Roadmap

* ~~Document compiler library API~~
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