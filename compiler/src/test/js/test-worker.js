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

// Web worker script for worker-harness.js, similar to worker.js of the UI.

Error.stackTraceLimit = 50;
(async function() {
    const teavmSupport = await import("./compiler/compiler.wasm-runtime.js");
    const teavm = await teavmSupport.load(new URL("compiler/compiler.wasm", self.location.href).href, {
        stackDeobfuscator: {
            enabled: true
        }
    });
    teavm.exports.installWorker();
})();
