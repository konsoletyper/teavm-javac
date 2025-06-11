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

package org.teavm.javac.ui;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.javac.protocol.CompilationResultMessage;
import org.teavm.javac.protocol.CompileMessage;
import org.teavm.javac.protocol.CompilerDiagnosticMessage;
import org.teavm.javac.protocol.ErrorMessage;
import org.teavm.javac.protocol.LoadStdlibMessage;
import org.teavm.javac.protocol.TeaVMDiagnosticMessage;
import org.teavm.javac.protocol.WorkerMessage;
import org.teavm.javac.ui.codemirror.CodeMirror;
import org.teavm.javac.ui.codemirror.CodeMirrorConfig;
import org.teavm.javac.ui.codemirror.MarkOptions;
import org.teavm.javac.ui.codemirror.TextLocation;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSObject;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.dom.events.Registration;
import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLIFrameElement;
import org.teavm.jso.json.JSON;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.workers.Worker;

public final class Client {
    private Client() {
    }

    private static final String DIAGNOSTICS_GUTTER = "diagnostics";
    private static final int WARNING = 1;
    private static final int ERROR = 2;

    private static Worker worker;
    private static HTMLButtonElement compileButton = (HTMLButtonElement) HTMLDocument.current()
            .getElementById("compile-button");
    private static HTMLButtonElement examplesButton = (HTMLButtonElement) HTMLDocument.current().getElementById(
            "choose-example");
    private static HTMLElement stdoutElement;
    private static int lastId;
    private static CodeMirror codeMirror;
    private static PositionIndexer positionIndexer;
    private static HTMLElement[] gutterElements;
    private static int[] gutterSeverity;
    private static HTMLElement examplesDialog = HTMLDocument.current().getElementById("examples");
    private static String examplesBaseUrl = "examples/";
    private static final Map<String, ExampleCategory> categories = new HashMap<>();
    private static String workerLocation;
    private static String stdlibLocation;
    private static String runtimeStdlibLocation;

    @JSExport
    public static void setupUI(ClientOptions options) {
        workerLocation = options.getWorkerLocation();
        stdlibLocation = options.getStdlibLocation();
        runtimeStdlibLocation = options.getRuntimeStdlibLocation();
        frame = (HTMLIFrameElement) HTMLDocument.current().getElementById("result");
        initEditor();
        initExamples();
        initStdout();
        init();
        compileButton.addEventListener("click", event -> {
            compileButton.setDisabled(true);
            compile().then(code -> {
                if (code != null) {
                    executeCode(code);
                }
                return null;
            }).onSettled(() -> {
                compileButton.setDisabled(false);
                return null;
            });
        });
    }

    private static void initEditor() {
        CodeMirrorConfig config = JSObjects.create();
        config.setIndentUnit(4);
        config.setLineNumbers(true);
        config.setGutters(new String[] { DIAGNOSTICS_GUTTER, "CodeMirror-linenumbers" });
        codeMirror = CodeMirror.fromTextArea(HTMLDocument.current().getElementById("source-code"), config);

        loadCode();
        Window.current().onBeforeUnload(e -> saveCode());
        Window.current().onBlur(e -> saveCode());
    }

    private static void initExamples() {
        var document = HTMLDocument.current();

        var chooseButton = (HTMLButtonElement) document.getElementById("choose-example");
        chooseButton.onClick(event -> showExamples());

        var cancelButton = (HTMLButtonElement) document.getElementById("cancel-example-selection");
        cancelButton.onClick(event -> hideExamples());

        var request = new XMLHttpRequest();
        request.open("get", examplesBaseUrl + "examples.json");
        request.onComplete(() -> {
            loadExamples((JsonObject) JSON.parse(request.getResponseText()));
            renderExamples();
            examplesButton.setDisabled(false);
        });
        request.send();
    }

    private static void loadExamples(JsonObject object) {
        for (String key : JSObjects.keys(object)) {
            ExampleCategory category = new ExampleCategory();
            JsonObject categoryObject = object.get(key);
            category.title = categoryObject.getAsString("title");
            categories.put(key, category);

            JsonObject categoryItems = categoryObject.get("items");
            for (String itemKey : JSObjects.keys(categoryItems)) {
                String itemTitle = categoryItems.getAsString(itemKey);
                category.items.put(itemKey, itemTitle);
            }
        }
    }

    private static void renderExamples() {
        var document = HTMLDocument.current();
        var container = document.getElementById("examples-content");
        for (var categoryEntry : categories.entrySet()) {
            ExampleCategory category = categoryEntry.getValue();
            container.appendChild(document.createElement("h3").withText(category.title));
            for (var entry : category.items.entrySet()) {
                var itemElement = document.createElement("div");
                itemElement.appendChild(document.createElement("span").withText(entry.getValue()));
                itemElement.setClassName("example-item");
                itemElement.onClick(event -> loadExample(categoryEntry.getKey(), entry.getKey()));
                container.appendChild(itemElement);
            }
        }
    }

    private static void loadExample(String category, String item) {
        var document = HTMLDocument.current();
        var progressElement = document.getElementById("examples-content-progress");
        progressElement.getStyle().setProperty("display", "block");

        var xhr = new XMLHttpRequest();
        xhr.open("get", examplesBaseUrl + category + "/" + item + ".java");
        xhr.onComplete(() -> {
            String code = xhr.getResponseText();
            codeMirror.setValue(code);
            hideExamples();
            progressElement.getStyle().setProperty("display", "none");
        });
        xhr.send();
    }

    private static void showExamples() {
        examplesDialog.getClassList().add("modal-open");
    }

    private static void hideExamples() {
        examplesDialog.getClassList().remove("modal-open");
    }

    private static void initStdout() {
        stdoutElement = HTMLDocument.current().getElementById("stdout");
        Window.current().onMessage((MessageEvent event) -> {
            var request = (FrameCommand) event.getData();
            if (!JSObjects.isUndefined(request.getCommand()) && request.getCommand() != null) {
                if (request.getCommand().equals("stdout")) {
                    var stdoutCommand = (FrameStdoutCommand) request;
                    addToConsole(stdoutCommand.getLine(), false, false);
                } else if (request.getCommand().equals("stderr")) {
                    var stdoutCommand = (FrameStdoutCommand) request;
                    addToConsole(stdoutCommand.getLine(), false, true);
                }
            }
        });
    }

    private static void addTextToConsole(String text, boolean compileTime, boolean error) {
        int last = 0;
        for (int i = 0; i < text.length(); ++i) {
            if (text.charAt(i) == '\n') {
                addToConsole(text.substring(last, i), compileTime, error);
                last = i + 1;
            }
        }
        addToConsole(text.substring(last), compileTime, error);
    }

    private static void addToConsole(String line, boolean compileTime, boolean error) {
        var lineElem = HTMLDocument.current().createElement("div").withText(line);
        if (compileTime) {
            lineElem.setClassName("compile-time");
        }
        if (error) {
            lineElem.setClassName("error");
        }
        stdoutElement.appendChild(lineElem);
        stdoutElement.setScrollTop(Math.max(0, stdoutElement.getScrollHeight() - stdoutElement.getClientHeight()));
    }

    private static JSPromise<Boolean> init() {
        compileButton.setDisabled(true);

        worker = new Worker(workerLocation);
        return waitForWorker()
                .flatThen(v -> loadStandardLibrary());
    }

    private static JSPromise<Void> waitForWorker() {
        return new JSPromise<>((resolve, reject) -> {
            var regHolder = new Object() {
                Registration reg;
            };
            regHolder.reg = worker.onMessage(event -> {
                var message = (WorkerMessage) event.getData();
                if (message.getCommand().equals("initialized")) {
                    regHolder.reg.dispose();
                    System.out.println("Worker reported initialization");
                    resolve.accept(null);
                }
            });
        });
    }

    private static JSPromise<Boolean> loadStandardLibrary() {
        LoadStdlibMessage loadStdlib = createMessage("load-classlib");
        loadStdlib.setUrl(stdlibLocation);
        loadStdlib.setRuntimeUrl(runtimeStdlibLocation);
        worker.postMessage(loadStdlib);
        return waitForResponse(loadStdlib).then(loadStdlibResult -> {
            if (!loadStdlibResult.getCommand().equals("ok")) {
                Window.alert("Could not load standard library: " + ((ErrorMessage) loadStdlibResult).getText());
                return false;
            }
            compileButton.setDisabled(false);
            return true;
        });
    }

    private static JSPromise<Int8Array> compile() {
        stdoutElement.clear();

        var allMarks = codeMirror.getAllMarks();
        for (int i = 0; i < allMarks.getLength(); ++i) {
            allMarks.get(i).clear();
        }
        codeMirror.clearGutter(DIAGNOSTICS_GUTTER);
        gutterElements = new HTMLElement[codeMirror.lineCount()];
        gutterSeverity = new int[codeMirror.lineCount()];

        CompileMessage request = createMessage("compile");
        String code = codeMirror.getValue();
        positionIndexer = new PositionIndexer(code);
        request.setText(code);
        worker.postMessage(request);

        return waitForCompilationResult(request);
    }

    private static JSPromise<Int8Array> waitForCompilationResult(CompileMessage request) {
        return waitForResponse(request).flatThen(response -> {
            switch (response.getCommand()) {
                case "compilation-complete": {
                    var compilationResult = (CompilationResultMessage) response;
                    return JSPromise.resolve(compilationResult.getStatus().equals("successful")
                            ? compilationResult.getScript()
                            : null);
                }
                case "compiler-diagnostic":
                    handleCompilerDiagnostic((CompilerDiagnosticMessage) response);
                    break;
                case "diagnostic":
                    handleDiagnostic((TeaVMDiagnosticMessage) response);
                    break;
            }
            return waitForCompilationResult(request);
        });
    }

    private static void handleCompilerDiagnostic(CompilerDiagnosticMessage request) {
        StringBuilder sb = new StringBuilder();
        switch (request.getSeverity()) {
            case "error":
                sb.append("ERROR ");
                break;
            case "warning":
                sb.append("WARNING ");
                break;
        }

        if (request.getFileName() != null) {
            sb.append("at " + request.getFileName());
            if (request.getLineNumber() >= 0) {
                sb.append("(").append(request.getLineNumber() + 1).append(":").append(request.getColumnNumber() + 1)
                        .append(")");
            }
            sb.append(' ');
        }
        sb.append(request.getMessage());
        addTextToConsole(sb.toString(), true, false);

        if (request.getStartPosition() >= 0) {
            displayMarkInEditor(request);
        }
    }

    private static void handleDiagnostic(TeaVMDiagnosticMessage request) {
        StringBuilder sb = new StringBuilder(request.getSeverity()).append(' ');

        if (request.getFileName() != null) {
            sb.append("at " + request.getFileName());
            if (request.getLineNumber() >= 0) {
                sb.append(":").append(request.getLineNumber() + 1);
            }
            sb.append(' ');
        }
        sb.append(request.getText());
        addTextToConsole(sb.toString(), true, false);

        if (request.getLineNumber() >= 0) {
            int severity;
            switch (request.getSeverity()) {
                case "ERROR":
                    severity = ERROR;
                    break;
                case "WARNING":
                    severity = WARNING;
                    break;
                default:
                    return;
            }
            addGutterDiagnostic(severity, request.getLineNumber(), request.getText());
        }
    }

    private static void displayMarkInEditor(CompilerDiagnosticMessage request) {
        Position start = positionIndexer.getPositionAt(request.getStartPosition(), true);
        if (start.line >= gutterElements.length) {
            return;
        }

        int endPosition = request.getEndPosition();
        if (endPosition == request.getStartPosition()) {
            endPosition++;
        }
        Position end = positionIndexer.getPositionAt(endPosition, false);

        MarkOptions options = JSObjects.create();
        int gutterSeverity;
        switch (request.getSeverity()) {
            case "error":
                options.setClassName("red-wave");
                gutterSeverity = 2;
                break;
            case "warning":
                options.setClassName("yellow-wave");
                gutterSeverity = 1;
                break;
            default:
                return;
        }
        options.setInclusiveLeft(true);
        options.setInclusiveRight(true);
        options.setTitle(request.getMessage());

        codeMirror.markText(
                TextLocation.create(start.line, start.column),
                TextLocation.create(end.line, end.column),
                options);

        addGutterDiagnostic(gutterSeverity, start.line, request.getMessage());
    }

    private static void addGutterDiagnostic(int severity, int line, String message) {
        if (gutterSeverity[line] < severity) {
            gutterSeverity[line] = severity;
        }

        String gutterClassName;
        switch (gutterSeverity[line]) {
            case ERROR:
                gutterClassName = "exclamation-sign gutter-error";
                break;
            case WARNING:
                gutterClassName = "warning-sign gutter-warning";
                break;
            default:
                return;
        }

        HTMLElement element = gutterElements[line];
        if (element == null) {
            element = HTMLDocument.current().createElement("span");
            gutterElements[line] = element;
        }

        element.setClassName("glyphicon glyphicon-" + gutterClassName);

        String title = element.getTitle();
        title = !title.isEmpty() ? title + "\n" + message : message;
        element.setTitle(title);
        codeMirror.setGutterMarker(line, DIAGNOSTICS_GUTTER, element);
    };

    private static <T extends WorkerMessage> T createMessage(String command) {
        T message = JSObjects.createWithoutProto();
        message.setCommand(command);
        message.setId(String.valueOf(lastId++));
        return message;
    }

    private static <T extends WorkerMessage> JSPromise<T> waitForResponse(WorkerMessage request) {
        return new JSPromise<>((resolve, reject) -> {
            class ResponseWait {
                EventListener<MessageEvent> listener;

                void run() {
                    listener = event -> {
                        if (!isMessage(event.getData())) {
                            return;
                        }
                        var message = (WorkerMessage) event.getData();
                        if (message.getId().equals(request.getId())) {
                            worker.removeEventListener("message", listener);
                            //noinspection unchecked
                            resolve.accept((T) message);
                        }
                    };
                    worker.addEventListener("message", listener);
                }
            }
            new ResponseWait().run();
        });
    }

    @JSBody(params = "object", script = "return 'id' in object && typeof object.id === 'string';")
    private static native boolean isMessage(JSObject object);

    private static HTMLIFrameElement frame;
    private static EventListener<MessageEvent> listener;

    private static void executeCode(Int8Array code) {
        if (frame != null) {
            frame.delete();
        }

        var document = Window.current().getDocument();
        frame = (HTMLIFrameElement) document.createElement("iframe");
        frame.setSourceAddress("frame.html");
        frame.setWidth("1px");
        frame.setHeight("1px");
        frame.setClassName("result");

        listener = event -> {
            var command = (FrameCommand) event.getData();
            if (command.getCommand().equals("ready")) {
                FrameCodeCommand codeCommand = JSObjects.create();
                codeCommand.setCommand("code");
                codeCommand.setCode(code);
                frame.getContentWindow().postMessage(codeCommand, "*");
                Window.current().removeEventListener("message", listener);
                listener = null;
            }
        };
        Window.current().addEventListener("message", listener);

        document.getElementById("result-container").appendChild(frame);
    }

    private static void loadCode() {
        String code = Window.current().getLocalStorage().getItem("teavm-java-code");
        if (code != null) {
            codeMirror.setValue(code);
        }
    }

    private static void saveCode() {
        Window.current().getLocalStorage().setItem("teavm-java-code", codeMirror.getValue());
    }

    static class ExampleCategory {
        String title;
        Map<String, String> items = new LinkedHashMap<>();
    }
}
