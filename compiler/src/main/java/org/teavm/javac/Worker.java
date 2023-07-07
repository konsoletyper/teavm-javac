/*
 *  Copyright 2025 Alexey Andreev.
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

package org.teavm.javac;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import org.teavm.javac.protocol.CompilationResultMessage;
import org.teavm.javac.protocol.CompileMessage;
import org.teavm.javac.protocol.CompilerDiagnosticMessage;
import org.teavm.javac.protocol.ErrorMessage;
import org.teavm.javac.protocol.LoadStdlibMessage;
import org.teavm.javac.protocol.TeaVMDiagnosticMessage;
import org.teavm.javac.protocol.TeaVMPhaseMessage;
import org.teavm.javac.protocol.WorkerMessage;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;
import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.vm.TeaVMPhase;

public final class Worker {
    private boolean isBusy;
    private final String SOURCE_FILE_NAME = "Main.java";
    private String mainClass;
    private final Compiler compiler;

    Worker(Compiler compiler) {
        this.compiler = compiler;
        Window.worker().addEventListener("message", (MessageEvent event) -> {
            handleEvent(event);
        });
        WorkerMessage response = JSObjects.createWithoutProto();
        response.setCommand("initialized");
        Window.worker().postMessage(response);
    }

    private void handleEvent(MessageEvent event) {
        var request = (WorkerMessage) event.getData();
        try {
            processResponse(request);
        } catch (Throwable e) {
            log("Error occurred");
            e.printStackTrace();
            Window.worker().postMessage(createErrorResponse(request, "Error occurred processing message: "
                    + e.getMessage()));
        }
    }

    private long initializationStartTime;

    private void processResponse(WorkerMessage request) throws Exception {
        log("Message received: " + request.getId());

        if (isBusy) {
            log("Responded busy status");
            Window.worker().postMessage(createErrorResponse(request, "Busy"));
            return;
        }

        isBusy = true;
        switch (request.getCommand()) {
            case "load-classlib":
                var loadLibReq = (LoadStdlibMessage) request;
                init(request, loadLibReq.getUrl(), loadLibReq.getRuntimeUrl(), success -> {
                    if (success) {
                        respondOk(request);
                    }
                    isBusy = false;
                });
                break;
            case "compile":
                compileAll((CompileMessage) request);
                log("Done processing message: " + request.getId());
                isBusy = false;
                break;
        }
    }

    private void compileAll(CompileMessage request) throws IOException {
        createSourceFile(request.getText());

        CompilationResultMessage response = JSObjects.createWithoutProto();
        response.setId(request.getId());
        response.setCommand("compilation-complete");

        if (doCompile(request) && detectMainClass(request) && generateWebAssembly(request.getId())) {
            response.setStatus("successful");
            response.setScript(readResultingFile());
        } else {
            response.setStatus("errors");
        }

        Window.worker().postMessage(response);
    }

    private void respondOk(WorkerMessage message) {
        WorkerMessage response = JSObjects.createWithoutProto();
        response.setCommand("ok");
        response.setId(message.getId());
        Window.worker().postMessage(response);
    }

    private <T extends ErrorMessage> T createErrorResponse(WorkerMessage request, String text) {
        T message = JSObjects.createWithoutProto();
        message.setId(request.getId());
        message.setCommand("error");
        message.setText(text);
        return message;
    }

    private void init(WorkerMessage request, String url, String runtimeUrl, Consumer<Boolean> next) {
        log("Initializing");

        initializationStartTime = System.currentTimeMillis();
        loadTeaVMClasslib(request, url, runtimeUrl, success -> {
            long end = System.currentTimeMillis();
            log("Initialized in " + (end - initializationStartTime) + " ms");
            next.accept(success);
        });
    }

    private boolean doCompile(WorkerMessage request) {
        var requestId = request.getId();
        var reg = compiler.onDiagnostic(diagnostic -> handleDiagnostic((JavaDiagnostic) diagnostic, requestId));
        var result = compiler.compile();
        reg.destroy();
        return result;
    }

    private void handleDiagnostic(JavaDiagnostic diagnostic, String requestId) {
        CompilerDiagnosticMessage response = JSObjects.createWithoutProto();
        response.setCommand("compiler-diagnostic");
        response.setId(requestId);

        response.setSeverity(diagnostic.getSeverity());
        response.setFileName(diagnostic.getFileName());

        response.setStartPosition(diagnostic.getStartPosition());
        response.setPosition(diagnostic.getPosition());
        response.setEndPosition( diagnostic.getEndPosition());

        response.setLineNumber(diagnostic.getLineNumber());
        response.setColumnNumber(diagnostic.getColumnNumber());

        response.setMessage(diagnostic.getMessage());

        Window.worker().postMessage(response);
    }

    private void handleTeaVMDiagnostic(TeaVMDiagnostic diagnostic, String requestId) {
        CompilerDiagnosticMessage response = JSObjects.createWithoutProto();
        response.setCommand("diagnostic");
        response.setId(requestId);

        response.setSeverity(diagnostic.getSeverity());
        response.setFileName(diagnostic.getFileName());

        response.setLineNumber(diagnostic.getLineNumber());
        response.setColumnNumber(0);

        response.setMessage(diagnostic.getMessage());

        Window.worker().postMessage(response);
    }

    private long lastPhaseTime = System.currentTimeMillis();
    private TeaVMPhase lastPhase;

    private boolean detectMainClass(WorkerMessage request) throws IOException {
        var candidates = compiler.detectMainClasses();
        if (candidates.length != 1) {
            var text = candidates.length == 0 ? "Main method not found" : "Multiple main methods found";
            TeaVMDiagnosticMessage message = JSObjects.createWithoutProto();
            message.setId(request.getId());
            message.setCommand("diagnostic");
            message.setSeverity("ERROR");
            message.setText(text);
            message.setFileName(null);
            Window.worker().postMessage(message);
            return false;
        }

        mainClass = candidates[0];
        mainClass = mainClass.replace('/', '.');
        return true;
    }

    private boolean generateWebAssembly(String requestId) {
        var options = new WebAssemblyCompilationOptions() {
            @Override
            public JSString getOutputName() {
                return JSString.valueOf("app");
            }

            @Override
            public JSString getMainClass() {
                return JSString.valueOf(mainClass);
            }
        };
        var reg = compiler.onDiagnostic(diagnostic -> handleTeaVMDiagnostic((TeaVMDiagnostic) diagnostic, requestId));
        var result = compiler.generateWebAssembly(options);
        reg.destroy();
        return result;
    }

    private void reportPhase(WorkerMessage request, TeaVMPhase phase) {
        TeaVMPhaseMessage phaseMessage = JSObjects.createWithoutProto();
        phaseMessage.setId(request.getId());
        phaseMessage.setCommand("phase");
        phaseMessage.setPhase(phase.name());
        Window.worker().postMessage(phaseMessage);
    }

    private void loadTeaVMClasslib(WorkerMessage request, String url, String runtimeUrl, Consumer<Boolean> next) {
        File baseDir = new File("/teavm-stdlib");
        baseDir.mkdirs();

        JSPromise.all(JSArray.of(downloadFile(url), downloadFile(runtimeUrl)))
                .then(arr -> {
                    boolean success;
                    var file = arr.get(0);
                    var runtimeFile = arr.get(1);
                    try {
                        compiler.setSdk(file);
                        compiler.setTeaVMClasslib(runtimeFile);
                        success = true;
                    } catch (IOException e) {
                        success = false;
                        Window.worker().postMessage(createErrorResponse(request, "Error occurred downloading classlib: "
                                + e.getMessage()));
                    }
                    next.accept(success);
                    return null;
                });
    }


    private static JSPromise<Int8Array> downloadFile(String url) {
        return new JSPromise<>((resolve, reject) -> {
            var xhr = new XMLHttpRequest();
            xhr.open("GET", url, true);
            xhr.setResponseType("arraybuffer");
            xhr.onComplete(() -> {
                var buffer = (ArrayBuffer) xhr.getResponse();
                resolve.accept(new Int8Array(buffer));
            });
            xhr.onError(ignore -> reject.accept(new RuntimeException("Error downloading file: " + url)));
            xhr.send();
        });
    }

    private void createSourceFile(String content) throws IOException {
        compiler.addSourceFile(SOURCE_FILE_NAME, content);
    }

    private static void log(String message) {
        System.out.println(message);
    }

    private Int8Array readResultingFile() throws IOException {
        return compiler.getWebAssemblyOutputFile("app.wasm");
    }
}
