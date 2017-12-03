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

import org.teavm.interop.Async;
import org.teavm.javac.protocol.CompilationResultMessage;
import org.teavm.javac.protocol.CompileMessage;
import org.teavm.javac.protocol.CompilerDiagnosticMessage;
import org.teavm.javac.protocol.ErrorMessage;
import org.teavm.javac.protocol.LoadStdlibMessage;
import org.teavm.javac.protocol.TeaVMDiagnosticMessage;
import org.teavm.javac.protocol.WorkerMessage;
import org.teavm.javac.ui.codemirror.CodeMirror;
import org.teavm.javac.ui.codemirror.CodeMirrorConfig;
import org.teavm.javac.ui.codemirror.Mark;
import org.teavm.javac.ui.codemirror.MarkOptions;
import org.teavm.javac.ui.codemirror.TextLocation;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSString;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLIFrameElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.HTMLMetaElement;
import org.teavm.jso.json.JSON;
import org.teavm.jso.workers.Worker;
import org.teavm.platform.async.AsyncCallback;

public final class Client {
    private Client() {
    }

    private static final String DIAGNOSTICS_GUTTER = "diagnostics";
    private static final int WARNING = 1;
    private static final int ERROR = 2;

    private static Worker worker;
    private static HTMLInputElement compileButton = HTMLDocument.current().getElementById("compile-button").cast();
    private static HTMLElement stdoutElement;
    private static int lastId;
    private static CodeMirror codeMirror;
    private static PositionIndexer positionIndexer;
    private static HTMLElement[] gutterElements;
    private static int[] gutterSeverity;

    public static void main(String[] args) {
        frame = (HTMLIFrameElement) HTMLDocument.current().getElementById("result");
        initEditor();
        initStdout();
        init();
        compileButton.addEventListener("click", event -> {
            new Thread(() -> {
                String generatedCode;
                try {
                    compileButton.setDisabled(true);
                    generatedCode = compile();
                } finally {
                    compileButton.setDisabled(false);
                }
                if (generatedCode != null) {
                    executeCode(generatedCode);
                }
            }).start();
        });
    }

    private static void initEditor() {
        CodeMirrorConfig config = createJs();
        config.setIndentUnit(4);
        config.setLineNumbers(true);
        config.setGutters(new String[] { DIAGNOSTICS_GUTTER, "CodeMirror-linenumbers" });
        codeMirror = CodeMirror.fromTextArea(HTMLDocument.current().getElementById("source-code"), config);

        loadCode();
        Window.current().listenBeforeOnload(e -> saveCode());
        Window.current().listenBlur(e -> saveCode());
    }

    private static void initStdout() {
        stdoutElement = HTMLDocument.current().getElementById("stdout");
        Window.current().addEventListener("message", (MessageEvent event) -> {
            FrameCommand request = (FrameCommand) JSON.parse(((JSString) event.getData()).stringValue());
            if (request.getCommand().equals("stdout")) {
                FrameStdoutCommand stdoutCommand = (FrameStdoutCommand) request;
                addToConsole(stdoutCommand.getLine(), false);
            }
        });
    }

    private static void addTextToConsole(String text, boolean compileTime) {
        int last = 0;
        for (int i = 0; i < text.length(); ++i) {
            if (text.charAt(i) == '\n') {
                addToConsole(text.substring(last, i), compileTime);
                last = i + 1;
            }
        }
        addToConsole(text.substring(last), compileTime);
    }

    private static void addToConsole(String line, boolean compileTime) {
        HTMLElement lineElem = HTMLDocument.current().createElement("div").withText(line);
        if (compileTime) {
            lineElem.setClassName("compile-time");
        }
        stdoutElement.appendChild(lineElem);
        stdoutElement.setScrollTop(Math.max(0, stdoutElement.getScrollHeight() - stdoutElement.getClientHeight()));
    }

    private static boolean init() {
        compileButton.setDisabled(true);

        HTMLMetaElement workerLocationElem = HTMLDocument.current().getHead()
                .querySelector("[property=workerLocation]").cast();
        if (workerLocationElem == null) {
            Window.alert("Can't initialize: not workerLocation meta tag specified");
            return false;
        }

        HTMLMetaElement stdlibLocationElem = HTMLDocument.current().getHead()
                .querySelector("[property=stdlibLocation]").cast();
        if (workerLocationElem == null) {
            Window.alert("Can't initialize: not stdlibLocation meta tag specified");
            return false;
        }

        worker = Worker.create(workerLocationElem.getContent());

        LoadStdlibMessage loadStdlib = createMessage("load-classlib");
        loadStdlib.setUrl(stdlibLocationElem.getContent());
        worker.postMessage(loadStdlib);
        WorkerMessage loadStdlibResult = waitForResponse(loadStdlib);
        if (!loadStdlibResult.getCommand().equals("ok")) {
            Window.alert("Could not load standard library: " + ((ErrorMessage) loadStdlibResult).getText());
            return false;
        }

        compileButton.setDisabled(false);
        return true;
    }

    private static String compile() {
        stdoutElement.clear();

        JSArrayReader<Mark> allMarks = codeMirror.getAllMarks();
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

        while (true) {
            WorkerMessage response = waitForResponse(request);
            switch (response.getCommand()) {
                case "compilation-complete": {
                    CompilationResultMessage compilationResult = (CompilationResultMessage) response;
                    return compilationResult.getStatus().equals("successful") ? compilationResult.getScript() : null;
                }
                case "compiler-diagnostic":
                    handleCompilerDiagnostic((CompilerDiagnosticMessage) response);
                    break;
                case "diagnostic":
                    handleDiagnostic((TeaVMDiagnosticMessage) response);
                    break;
            }
        }
    }

    private static void handleCompilerDiagnostic(CompilerDiagnosticMessage request) {
        StringBuilder sb = new StringBuilder();
        switch (request.getKind()) {
            case "ERROR":
                sb.append("ERROR ");
                break;
            case "WARNING":
            case "MANDATORY_WARNING":
                sb.append("WARNING ");
                break;
        }

        if (request.getObject() != null) {
            sb.append("at " + request.getObject().getName());
            if (request.getLineNumber() >= 0) {
                sb.append("(").append(request.getLineNumber() + 1).append(":").append(request.getColumnNumber() + 1)
                        .append(")");
            }
            sb.append(' ');
        }
        sb.append(request.getMessage());
        addTextToConsole(sb.toString(), true);

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
        addTextToConsole(sb.toString(), true);

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

        MarkOptions options = createJs();
        int gutterSeverity;
        switch (request.getKind()) {
            case "ERROR":
                options.setClassName("red-wave");
                gutterSeverity = 2;
                break;
            case "WARNING":
            case "MANDATORY_WARNING":
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
    }

    @JSBody(script = "return {};")
    private static native  <T extends JSObject> T createJs();

    private static <T extends WorkerMessage> T createMessage(String command) {
        T message = createJs();
        message.setCommand(command);
        message.setId(String.valueOf(lastId++));
        return message;
    }

    @Async
    private static native <T extends WorkerMessage> T waitForResponse(WorkerMessage request);
    private static void waitForResponse(WorkerMessage request, AsyncCallback<WorkerMessage> callback) {
        class ResponseWait {
            EventListener<MessageEvent> listener;

            void run() {
                listener = event -> {
                    if (!isMessage(event.getData())) {
                        return;
                    }
                    WorkerMessage message = (WorkerMessage) event.getData();
                    if (message.getId().equals(request.getId())) {
                        worker.removeEventListener("message", listener);
                        callback.complete(message);
                    }
                };
                worker.addEventListener("message", listener);
            }
        }
        new ResponseWait().run();
    }

    @JSBody(params = "object", script = "return 'id' in object && typeof object.id === 'string';")
    private static native boolean isMessage(JSObject object);

    private static HTMLIFrameElement frame;
    private static EventListener<MessageEvent> listener;

    private static void executeCode(String code) {
        if (frame != null) {
            frame.delete();
        }

        HTMLDocument document = Window.current().getDocument();
        frame = (HTMLIFrameElement) document.createElement("iframe");
        frame.setSourceAddress("frame.html");
        frame.setWidth("1px");
        frame.setHeight("1px");
        frame.setClassName("result");

        listener = event -> {
            FrameCommand command = (FrameCommand) JSON.parse(((JSString) event.getData()).stringValue());
            if (command.getCommand().equals("ready")) {
                FrameCodeCommand codeCommand = createJs();
                codeCommand.setCommand("code");
                codeCommand.setCode(code);
                frame.getContentWindow().postMessage(JSString.valueOf(JSON.stringify(codeCommand)), "*");
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
}
