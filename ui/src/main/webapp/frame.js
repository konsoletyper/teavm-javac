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

window.addEventListener("message", function(event) {
    var request = JSON.parse(event.data);
    appendFile(request.code + "\nmain();\n", function() {
        event.source.postMessage(JSON.stringify({ status: "loaded" }), "*");
    },
    function (error) {
        event.source.postMessage(JSON.stringify({ status: "failed", errorMessage: error }), "*");
    });
});

function appendFile(file, callback, errorCallback) {
    var script = document.createElement("script");
    script.onload = function() {
        callback();
    };
    script.onerror = function() {
        errorCallback("failed to load script" + fileName);
    };
    script.text = file;
    document.body.appendChild(script);
}

function start() {
    window.parent.postMessage(JSON.stringify({ command: "ready" }), "*");
}

var $stdoutBuffer = "";
function $rt_putStdoutCustom(ch) {
    if (ch === 0xA) {
        window.parent.postMessage(JSON.stringify({ command: "stdout", line: $stdoutBuffer }), "*");
        $stdoutBuffer = "";
    } else {
        $stdoutBuffer += String.fromCharCode(ch);
    }
}

var $stderrBuffer = "";
function $rt_putStderrCustom(ch) {
    if (ch === 0xA) {
        window.parent.postMessage(JSON.stringify({ command: "stderr", line: $stderrBuffer }), "*");
        $stderrBuffer = "";
    } else {
        $stderrBuffer += String.fromCharCode(ch);
    }
}