/*
 *  Copyright 2017 Alexey Andreev.
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

import { load } from './compiler.wasm-runtime.js'

window.addEventListener("message", async function(event) {
    let request = event.data;
    let module;
    try {
         module = await load(request.code, {
            stackDeobfuscator: {
                enabled: true
            },
            installImports(o) {
                o.teavmConsole.putcharStdout = putStdout;
                o.teavmConsole.putcharStderr = putStderr;
            }
        });
    } catch (e) {
        event.source.postMessage({ status: "failed", errorMessage: e.message }, "*");
        return;
    }
    event.source.postMessage({ status: "loaded" }, "*");
    module.exports.main([]);
});

export function start() {
    window.parent.postMessage({ command: "ready" }, "*");
}

let stdoutBuffer = "";
function putStdout(ch) {
    if (ch === 0xA) {
        window.parent.postMessage({ command: "stdout", line: stdoutBuffer }, "*");
        stdoutBuffer = "";
    } else {
        stdoutBuffer += String.fromCharCode(ch);
    }
}

let stderrBuffer = "";
function putStderr(ch) {
    if (ch === 0xA) {
        window.parent.postMessage({ command: "stderr", line: stderrBuffer }, "*");
        stderrBuffer = "";
    } else {
        stderrBuffer += String.fromCharCode(ch);
    }
}