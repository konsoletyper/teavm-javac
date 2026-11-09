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

where `installWorker` installs a handler of the worker protocol in the current web worker, see [below](#using-compiler-in-a-web-worker).

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
        strictMode?: boolean, // true by default; produced module follows JVM spec in favour of speed
        obfuscated?: boolean, // true by default; produced module has `name` section stripped off
        assertionsRemoved?: boolean // false by default
        asyncEnabled?: boolean // true by default
    }): Promise<boolean>

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


### Using compiler in a web worker

Compilation is CPU-intensive and blocks the thread it runs on, so you'll likely want to run the compiler
in a web worker. The `worker` module provides both sides of this setup:

* the worker side is the `installWorker` function of the compiler library;
* the client side is a small separate WebAssembly module (`worker-client.wasm`), which spawns
  the worker and communicates with it.

Both are included in `dist.zip` (see [Building library from sources](#building-library-from-sources)).

First, create a worker script, e.g. `worker.js`:

```js
(async function() {
    let { load } = await import("./compiler.wasm-runtime.js");
    let teavm = await load("compiler.wasm");
    teavm.exports.installWorker();
})();
```

Then, on the page, load the client module and spawn the worker:

```js
import { load } from "./worker-client.wasm-runtime.js";

let clientLib = (await load("./worker-client.wasm")).exports;
let worker = await clientLib.createCompilerWorker("worker.js");
```

where `clientLib` is defined as follows:

```ts
declare interface WorkerClientLibrary {
    // Spawns worker from given script and waits until it reports that it's initialized
    createCompilerWorker(workerUrl: string): Promise<CompilerWorker>
}
```

`CompilerWorker` is defined as follows:

```ts
declare class CompilerWorker {
    // Makes worker download class libraries for javac and for TeaVM 
    // (see `setSdk` and `setTeaVMClasslib` above).
    // Should be called before first compilation.
    loadClasslib(sdkUrl: string, runtimeUrl: string): Promise<void>

    // Source files are stored on the client side and are sent to the worker 
    // on each `compile` call. Path should correspond to the package of the class,
    // e.g. `com/example/Foo.java`.
    addSourceFile(path: string, content: string)
    removeSourceFile(path: string)
    getSourceFile(path: string): string | null
    listSourceFiles(): string[]
    clearSourceFiles()

    onDiagnostic(listener: (WorkerDiagnostic) => void): WorkerListenerRegistration

    // Compiles all source files with javac and then generates WebAssembly module with TeaVM.
    // If `options.mainClass` is not specified, worker looks for a class with `main` method
    // and reports an error if there's none or more than one.
    // Rejects if worker fails unexpectedly; compilation errors are reported 
    // via diagnostics and `successful: false`.
    compile(options?: {
        outputName?: string, // "app" by default; base name for WebAssembly module
        mainClass?: string,
        strictMode?: boolean, // true by default
        obfuscated?: boolean, // true by default
        assertionsRemoved?: boolean, // false by default
        asyncEnabled?: boolean // true by default
    }): Promise<WorkerCompilationResult>

    // Terminates the worker, rejecting all pending requests
    terminate()
}

interface WorkerCompilationResult {
    successful: boolean
    wasm: Int8Array | null // generated WebAssembly module, if successful
}

interface WorkerDiagnostic {
    type: "javac" | "teavm"
    severity: "error" | "warning" | "other"
    fileName: string | null
    lineNumber: number
    message: string
    // The following properties are only available for "javac" diagnostics, otherwise -1
    columnNumber: number
    startPosition: number
    position: number
    endPosition: number
}

interface WorkerListenerRegistration {
    destroy()
}
```

Requests to the worker are processed one by one, so you don't have to wait for completion of a previous call
before making the next one. Note that source files and options are captured at the moment of `compile` call.

Example:

```js
worker.onDiagnostic(diagnostic => {
    console.log(diagnostic.type, diagnostic.severity, diagnostic.fileName, diagnostic.lineNumber,
        diagnostic.message);
});
await worker.loadClasslib("compile-classlib-teavm.bin", "runtime-classlib-teavm.bin");

worker.addSourceFile("Main.java", `
    import com.example.Greeter;
    public class Main {
        public static void main(String[] args) {
            System.out.println(Greeter.greet("world"));
        }
    }
`);
worker.addSourceFile("com/example/Greeter.java", `
    package com.example;
    public class Greeter {
        public static String greet(String name) {
            return "Hello, " + name + "!";
        }
    }
`);

let result = await worker.compile({ mainClass: "Main", obfuscated: false });
if (result.successful) {
    let app = await load(result.wasm);
    app.exports.main([]);
}
```

The client is written in Java (see `org.teavm.javac.worker.CompilerWorker`), so you can use it directly
from a TeaVM application by depending on the `worker` module, as `ui` module does.
Underlying messages exchanged with the worker are described by interfaces in `org.teavm.javac.protocol` package.


### Building library from sources

You need Java 21 installed on your machine.

Run

```
./gradlew :compiler:build
```

Library can be found at `compiler/build/distributions/dist.zip`.


## Roadmap

* ~~Document compiler library API~~
* ~~Run TeaVM tests against this compiler~~
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