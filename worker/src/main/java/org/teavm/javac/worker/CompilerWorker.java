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

package org.teavm.javac.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.teavm.javac.protocol.CompilationOptions;
import org.teavm.javac.protocol.CompilationResultMessage;
import org.teavm.javac.protocol.CompileMessage;
import org.teavm.javac.protocol.CompilerDiagnosticMessage;
import org.teavm.javac.protocol.ErrorMessage;
import org.teavm.javac.protocol.LoadStdlibMessage;
import org.teavm.javac.protocol.SourceFile;
import org.teavm.javac.protocol.TeaVMDiagnosticMessage;
import org.teavm.javac.protocol.WorkerMessage;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSError;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSUndefined;
import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.dom.events.Registration;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.workers.Worker;

/**
 * Client for the compiler worker. Spawns a web worker that runs the compiler (see {@code installWorker}
 * of the compiler library) and communicates with it via the protocol from {@code org.teavm.javac.protocol}.
 *
 * <p>Source files are kept on the client side and are sent to the worker with every {@link #compile} call.
 * Requests are queued, so it's safe to call methods without waiting for completion of previous calls.</p>
 */
@JSClass(name = "CompilerWorker")
public final class CompilerWorker {
    private final Worker worker;
    private final Map<String, String> sourceFiles = new LinkedHashMap<>();
    private final List<DiagnosticListenerHolder> diagnosticListeners = new ArrayList<>();
    private final Map<String, PendingRequest> pendingRequests = new HashMap<>();
    private JSPromise<Object> queue = JSPromise.resolve(null);
    private int lastId;
    private boolean terminated;

    private CompilerWorker(Worker worker) {
        this.worker = worker;
        worker.onMessage(this::handleMessage);
    }

    /**
     * Spawns worker from given script URL and waits until it reports initialization.
     */
    public static JSPromise<CompilerWorker> create(String workerUrl) {
        return new JSPromise<>((resolve, reject) -> {
            var worker = new Worker(workerUrl);
            var registrations = new Registration[2];
            registrations[0] = worker.onMessage(event -> {
                var data = event.getData();
                if (isObject(data) && JSObjects.hasProperty(data, "command")
                        && "initialized".equals(((WorkerMessage) data).getCommand())) {
                    registrations[0].dispose();
                    registrations[1].dispose();
                    resolve.accept(new CompilerWorker(worker));
                }
            });
            registrations[1] = worker.onError(event -> {
                registrations[0].dispose();
                registrations[1].dispose();
                worker.terminate();
                reject.accept(new JSError("Error initializing worker: " + event.getMessage()));
            });
        });
    }

    @JSExport
    public void addSourceFile(String path, String content) {
        sourceFiles.put(path, content);
    }

    @JSExport
    public void removeSourceFile(String path) {
        sourceFiles.remove(path);
    }

    @JSExport
    public String getSourceFile(String path) {
        return sourceFiles.get(path);
    }

    @JSExport
    public String[] listSourceFiles() {
        return sourceFiles.keySet().toArray(new String[0]);
    }

    @JSExport
    public void clearSourceFiles() {
        sourceFiles.clear();
    }

    @JSExport
    public ListenerRegistration onDiagnostic(WorkerDiagnosticListener listener) {
        var holder = new DiagnosticListenerHolder(listener);
        diagnosticListeners.add(holder);
        return new ListenerRegistration(() -> diagnosticListeners.remove(holder));
    }

    /**
     * Makes worker download class libraries for javac and for TeaVM. Must be called before first compilation.
     */
    @JSExport
    public JSPromise<JSUndefined> loadClasslib(String sdkUrl, String runtimeUrl) {
        return enqueue(() -> {
            LoadStdlibMessage request = createMessage("load-classlib");
            request.setUrl(sdkUrl);
            request.setRuntimeUrl(runtimeUrl);
            return send(request).then(_ -> JSUndefined.instance());
        });
    }

    /**
     * Compiles all source files, added to this client, and generates WebAssembly module.
     *
     * @param options compilation options, may be {@code null} or {@code undefined}.
     */
    @JSExport
    public JSPromise<CompilationResult> compile(CompilationOptions options) {
        var files = new JSArray<SourceFile>();
        for (var entry : sourceFiles.entrySet()) {
            SourceFile file = JSObjects.createWithoutProto();
            file.setPath(entry.getKey());
            file.setContent(entry.getValue());
            files.push(file);
        }
        var optionsCopy = copyOptions(options);
        return enqueue(() -> {
            CompileMessage request = createMessage("compile");
            request.setFiles(files);
            request.setOptions(optionsCopy);
            return send(request).then(response -> {
                var result = (CompilationResultMessage) response;
                var successful = "successful".equals(result.getStatus());
                return new CompilationResult() {
                    @Override
                    public boolean isSuccessful() {
                        return successful;
                    }

                    @Override
                    public Int8Array getWasm() {
                        return successful ? result.getScript() : null;
                    }
                };
            });
        });
    }

    /**
     * Terminates the worker. All pending requests get rejected.
     */
    @JSExport
    public void terminate() {
        if (terminated) {
            return;
        }
        terminated = true;
        worker.terminate();
        var requests = List.copyOf(pendingRequests.values());
        pendingRequests.clear();
        for (var request : requests) {
            request.reject.accept(new JSError("Worker terminated"));
        }
    }

    private static CompilationOptions copyOptions(CompilationOptions options) {
        CompilationOptions result = JSObjects.createWithoutProto();
        if (options == null || JSObjects.isUndefined(options)) {
            return result;
        }
        if (isDefined(options.getOutputName())) {
            result.setOutputName(options.getOutputName());
        }
        if (isDefined(options.getMainClass())) {
            result.setMainClass(options.getMainClass());
        }
        if (isDefined(options.getStrictMode())) {
            result.setStrictMode(options.getStrictMode());
        }
        if (isDefined(options.getObfuscated())) {
            result.setObfuscated(options.getObfuscated());
        }
        if (isDefined(options.getAssertionsRemoved())) {
            result.setAssertionsRemoved(options.getAssertionsRemoved());
        }
        if (isDefined(options.getAsyncEnabled())) {
            result.setAsyncEnabled(options.getAsyncEnabled());
        }
        return result;
    }

    private static boolean isDefined(JSObject value) {
        return value != null && !JSObjects.isUndefined(value);
    }

    private <T> JSPromise<T> enqueue(Supplier<JSPromise<T>> action) {
        if (terminated) {
            return JSPromise.reject(new JSError("Worker terminated"));
        }
        JSPromise<T> result = queue.flatThen(_ -> action.get());
        queue = result.then(_ -> null, _ -> null);
        return result;
    }

    private JSPromise<WorkerMessage> send(WorkerMessage request) {
        return new JSPromise<>((resolve, reject) -> {
            if (terminated) {
                reject.accept(new JSError("Worker terminated"));
                return;
            }
            pendingRequests.put(request.getId(), new PendingRequest(resolve::accept, reject::accept));
            worker.postMessage(request);
        });
    }

    private void handleMessage(MessageEvent event) {
        if (!isMessage(event.getData())) {
            return;
        }
        var message = (WorkerMessage) event.getData();
        var request = pendingRequests.get(message.getId());
        if (request == null) {
            return;
        }
        switch (message.getCommand()) {
            case "compiler-diagnostic":
                notifyDiagnostic(WorkerDiagnostic.fromJavac((CompilerDiagnosticMessage) message));
                break;
            case "diagnostic":
                notifyDiagnostic(WorkerDiagnostic.fromTeaVM((TeaVMDiagnosticMessage) message));
                break;
            case "phase":
                break;
            case "error":
                pendingRequests.remove(message.getId());
                request.reject.accept(new JSError(((ErrorMessage) message).getText()));
                break;
            default:
                pendingRequests.remove(message.getId());
                request.resolve.accept(message);
                break;
        }
    }

    private void notifyDiagnostic(WorkerDiagnostic diagnostic) {
        for (var holder : List.copyOf(diagnosticListeners)) {
            holder.listener.onDiagnostic(diagnostic);
        }
    }

    private <T extends WorkerMessage> T createMessage(String command) {
        T message = JSObjects.createWithoutProto();
        message.setCommand(command);
        message.setId(String.valueOf(lastId++));
        return message;
    }

    private static boolean isMessage(JSObject object) {
        return isObject(object) && JSObjects.hasProperty(object, "id") && JSObjects.hasProperty(object, "command");
    }

    private static boolean isObject(JSObject object) {
        return object != null && JSObjects.typeOf(object).equals("object");
    }

    // Keep JS functor in a typed field: passing it through generic collections makes TeaVM wrap it twice
    private static class DiagnosticListenerHolder {
        final WorkerDiagnosticListener listener;

        DiagnosticListenerHolder(WorkerDiagnosticListener listener) {
            this.listener = listener;
        }
    }

    private static class PendingRequest {
        final Consumer<WorkerMessage> resolve;
        final Consumer<Object> reject;

        PendingRequest(Consumer<WorkerMessage> resolve, Consumer<Object> reject) {
            this.resolve = resolve;
            this.reject = reject;
        }
    }
}
