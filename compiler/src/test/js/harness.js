/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

// Loaded by TeaVM browser runner as a JavaScript test. Receives a JSON request with Java sources as the only
// argument, compiles and runs them, and reports result as a single stdout line prefixed with RESULT_MARKER.
// Browser runner forwards this line to System.out of the JVM, where CompilerTestHarness picks it up.

import { load } from "./compiler/compiler.wasm-runtime.js";

const RESULT_MARKER = "TEAVM_JAVAC_TEST_RESULT ";

let environmentPromise = null;

function loadEnvironment() {
    if (environmentPromise === null) {
        environmentPromise = (async () => {
            const [compilerModule, sdk, classlib] = await Promise.all([
                load(new URL("compiler/compiler.wasm", import.meta.url).href, {
                    stackDeobfuscator: {
                        enabled: true
                    }
                }),
                fetchBinary("classlib/compile-classlib-teavm.bin"),
                fetchBinary("classlib/runtime-classlib-teavm.bin")
            ]);
            return { compilerLib: compilerModule.exports, sdk, classlib };
        })();
    }
    return environmentPromise;
}

async function fetchBinary(path) {
    const response = await fetch(new URL(path, import.meta.url));
    if (!response.ok) {
        throw new Error(`Could not load ${path}: ${response.status}`);
    }
    return new Int8Array(await response.arrayBuffer());
}

export async function main(args, callback) {
    try {
        const result = await runTest(JSON.parse(args[0]));
        $rt_putStdoutCustom(RESULT_MARKER + JSON.stringify(result) + "\n");
        callback(null);
    } catch (e) {
        callback(e instanceof Error ? e : new Error(String(e)));
    }
}

async function runTest(request) {
    const env = await loadEnvironment();
    const result = {
        status: null,
        diagnostics: [],
        stdout: "",
        stderr: "",
        exception: null
    };

    const compiler = env.compilerLib.createCompiler();
    compiler.onDiagnostic(diagnostic => {
        result.diagnostics.push({
            type: diagnostic.type,
            severity: diagnostic.severity,
            fileName: diagnostic.fileName,
            lineNumber: diagnostic.lineNumber,
            message: diagnostic.message
        });
    });
    compiler.setSdk(env.sdk);
    compiler.setTeaVMClasslib(env.classlib);
    for (const [path, content] of Object.entries(request.sources)) {
        compiler.addSourceFile(path, content);
    }

    if (!compiler.compile()) {
        result.status = "JAVAC_FAILED";
        return result;
    }
    if (!compiler.generateWebAssembly({ outputName: "app", mainClass: request.mainClass })) {
        result.status = "TEAVM_FAILED";
        return result;
    }

    const app = await load(compiler.getWebAssemblyOutputFile("app.wasm"), {
        installImports(imports) {
            imports.teavmConsole.putcharStdout = c => result.stdout += String.fromCharCode(c);
            imports.teavmConsole.putcharStderr = c => result.stderr += String.fromCharCode(c);
        }
    });
    try {
        app.exports.main([]);
        result.status = "OK";
    } catch (e) {
        result.status = "EXCEPTION";
        result.exception = e instanceof Error ? e.message : String(e);
    }
    return result;
}
