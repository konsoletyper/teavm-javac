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

// Same as harness.js, but compiles sources via the worker client library (worker-client.wasm), which runs
// the compiler in a web worker (see test-worker.js). Reports result in the same format as harness.js.

import { load } from "./compiler/compiler.wasm-runtime.js";
import { load as loadWorkerClient } from "./worker-client/worker-client.wasm-runtime.js";

const RESULT_MARKER = "TEAVM_JAVAC_TEST_RESULT ";

let workerPromise = null;

function getWorker() {
    if (workerPromise === null) {
        workerPromise = (async () => {
            const clientModule = await loadWorkerClient(
                new URL("worker-client/worker-client.wasm", import.meta.url).href, {
                    stackDeobfuscator: {
                        enabled: true
                    }
                });
            const worker = await clientModule.exports.createCompilerWorker(
                new URL("test-worker.js", import.meta.url).href);
            await worker.loadClasslib(
                new URL("classlib/compile-classlib-teavm.bin", import.meta.url).href,
                new URL("classlib/runtime-classlib-teavm.bin", import.meta.url).href);
            return worker;
        })();
        // Don't cache failure, otherwise all subsequent tests fail with confusing error
        workerPromise.catch(() => workerPromise = null);
    }
    return workerPromise;
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
    const worker = await getWorker();
    const result = {
        status: null,
        diagnostics: [],
        stdout: "",
        stderr: "",
        exception: null
    };

    worker.clearSourceFiles();
    for (const [path, content] of Object.entries(request.sources)) {
        worker.addSourceFile(path, content);
    }

    const registration = worker.onDiagnostic(diagnostic => {
        result.diagnostics.push({
            type: diagnostic.type,
            severity: diagnostic.severity,
            fileName: diagnostic.fileName,
            lineNumber: diagnostic.lineNumber,
            message: diagnostic.message
        });
    });
    let compilationResult;
    try {
        compilationResult = await worker.compile({ outputName: "app", mainClass: request.mainClass });
    } finally {
        registration.destroy();
    }

    if (!compilationResult.successful) {
        // Worker protocol does not tell which phase failed, so guess it from diagnostics
        const javacFailed = result.diagnostics.some(d => d.type === "javac" && d.severity === "error");
        result.status = javacFailed ? "JAVAC_FAILED" : "TEAVM_FAILED";
        return result;
    }

    const app = await load(compilationResult.wasm, {
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
