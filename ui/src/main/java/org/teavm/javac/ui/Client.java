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

    private static Worker worker;
    private static HTMLInputElement compileButton = HTMLDocument.current().getElementById("compile-button").cast();
    private static int lastId;
    private static CodeMirror codeMirror;
    private static PositionIndexer positionIndexer;
    private static HTMLElement[] gutterElements;
    private static int[] gutterSeverity;

    public static void main(String[] args) {
        initEditor();
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
            }
        }
    }

    private static void handleCompilerDiagnostic(CompilerDiagnosticMessage request) {
        if (request.getStartPosition() >= 0) {
            displayMarkInEditor(request);
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

        String gutterClassName;
        MarkOptions options = createJs();
        int newGutterSeverity = 0;
        switch (request.getKind()) {
            case "ERROR":
                options.setClassName("red-wave");
                gutterClassName = "exclamation-sign gutter-error";
                newGutterSeverity = 2;
                break;
            case "WARNING":
            case "MANDATORY_WARNING":
                options.setClassName("yellow-wave");
                gutterClassName = "warning-sign gutter-warning";
                newGutterSeverity = 1;
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

        HTMLElement element = gutterElements[start.line];
        if (element == null) {
            element = HTMLDocument.current().createElement("span");
            gutterElements[start.line] = element;
        }
        if (gutterSeverity[start.line] < newGutterSeverity) {
            gutterSeverity[start.line] = newGutterSeverity;
            element.setClassName("glyphicon glyphicon-" + gutterClassName);
        }

        String title = element.getTitle();
        title = !title.isEmpty() ? title + "\n" + request.getMessage() : request.getMessage();
        element.setTitle(title);
        codeMirror.setGutterMarker(start.line, DIAGNOSTICS_GUTTER, element);
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

        HTMLInputElement stdout = (HTMLInputElement) document.getElementById("stdout");
        stdout.setValue("");

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

        document.getBody().appendChild(frame);
    }
}
