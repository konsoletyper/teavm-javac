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

package org.teavm.javac;

import com.sun.tools.javac.api.JavacTool;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import org.teavm.ast.cache.InMemoryRegularMethodNodeCache;
import org.teavm.ast.cache.MethodNodeCache;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.classlib.impl.JCLPlugin;
import org.teavm.diagnostics.Problem;
import org.teavm.diagnostics.ProblemSeverity;
import org.teavm.interop.Async;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSString;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLIFrameElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.HTMLLinkElement;
import org.teavm.jso.impl.JSOPlugin;
import org.teavm.jso.json.JSON;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.InMemoryProgramCache;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramCache;
import org.teavm.model.ValueType;
import org.teavm.parsing.CompositeClassHolderSource;
import org.teavm.parsing.DirectoryClasspathClassHolderSource;
import org.teavm.platform.async.AsyncCallback;
import org.teavm.platform.plugin.PlatformPlugin;
import org.teavm.vm.TeaVM;
import org.teavm.vm.TeaVMBuilder;
import org.teavm.vm.TeaVMPhase;
import org.teavm.vm.TeaVMProgressFeedback;
import org.teavm.vm.TeaVMProgressListener;

public final class Client {
    private Client() {
    }

    public static void main(String[] args) throws IOException {
        init();
        HTMLDocument document = Window.current().getDocument();
        HTMLInputElement textArea = (HTMLInputElement) document.getElementById("source-code");
        document.getElementById("compile-button").addEventListener("click", event -> {
            try {
                createSourceFile(textArea.getValue());
                if (doCompile() && generateJavaScript()) {
                    File jsFile = new File("/js-out/classes.js");
                    if (jsFile.exists()) {
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                new FileInputStream(jsFile), "UTF-8"))) {
                            while (true) {
                                String line = reader.readLine();
                                if (line == null) {
                                    break;
                                }
                                sb.append(line).append('\n');
                            }
                        }
                        executeCode(sb.toString());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static void init() throws IOException {
        System.out.println("Initializing");

        long start = System.currentTimeMillis();
        createStdlib();
        loadTeaVMClasslib();
        long end = System.currentTimeMillis();

        System.out.println("Initialized in " + (end - start) + " ms");
    }

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
                FrameCodeCommand codeCommand = createEmpty();
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

    @JSBody(script = "return {}")
    private static native <T extends JSObject> T createEmpty();

    @JSBody(params = "data",
            script = "return URL.createObjectURL(new Blob([data], { type: 'application/octet-stream' }));")
    private static native String createObjectURL(Uint8Array data);

    @JSBody(params = { "target", "name" }, script = "target.download = name;")
    private static native void setDownload(HTMLLinkElement target, String name);

    private static boolean doCompile() throws IOException {
        JavaCompiler compiler = JavacTool.create();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                Arrays.asList(new File("/Hello.java")));
        OutputStreamWriter out = new OutputStreamWriter(System.out);

        new File("/out").mkdirs();
        JavaCompiler.CompilationTask task = compiler.getTask(out, fileManager, null,
                Arrays.asList("-verbose", "-d", "/out"), null, compilationUnits);
        boolean result = task.call();
        out.flush();

        return result;
    }

    private static long lastPhaseTime = System.currentTimeMillis();
    private static TeaVMPhase lastPhase;
    private static ClassHolderSource stdlibClassSource;
    private static ProgramCache programCache = new InMemoryProgramCache();
    private static MethodNodeCache astCache = new InMemoryRegularMethodNodeCache();

    static {
        Properties stdlibMapping = new Properties();
        stdlibMapping.setProperty("packagePrefix.java", "org.teavm.classlib");
        stdlibMapping.setProperty("classPrefix.java", "T");

        stdlibClassSource = new DirectoryClasspathClassHolderSource(new File("/teavm-stdlib"), stdlibMapping);
    }

    private static boolean generateJavaScript() {
        try {
            long start = System.currentTimeMillis();

            List<ClassHolderSource> classSources = new ArrayList<>();
            classSources.add(stdlibClassSource);
            classSources.add(new DirectoryClasspathClassHolderSource(new File("/out")));

            JavaScriptTarget jsTarget = new JavaScriptTarget();
            jsTarget.setMinifying(false);
            TeaVM teavm = new TeaVMBuilder(jsTarget)
                    .setClassSource(new CompositeClassHolderSource(classSources))
                    .build();
            teavm.setIncremental(true);
            teavm.setProgramCache(programCache);
            jsTarget.setAstCache(astCache);

            long pluginInstallationStart = System.currentTimeMillis();
            new JSOPlugin().install(teavm);
            new PlatformPlugin().install(teavm);
            new JCLPlugin().install(teavm);
            System.out.println("Plugins loaded in " + (System.currentTimeMillis() - pluginInstallationStart) + " ms");

            teavm.entryPoint("main", new MethodReference("Hello", "main", ValueType.parse(String[].class),
                    ValueType.VOID))
                    .withArrayValue(1, "java.lang.String");
            File outDir = new File("/js-out");
            outDir.mkdirs();

            System.out.println("TeaVM initialized in " + (System.currentTimeMillis() - start) + " ms");

            lastPhase = null;
            lastPhaseTime = System.currentTimeMillis();
            teavm.setProgressListener(new TeaVMProgressListener() {
                @Override
                public TeaVMProgressFeedback phaseStarted(TeaVMPhase phase, int count) {
                    if (phase != lastPhase) {
                        long newPhaseTime = System.currentTimeMillis();
                        if (lastPhase != null) {
                            System.out.println(lastPhase.name() + ": " + (newPhaseTime - lastPhaseTime) + " ms");
                        }
                        lastPhaseTime = newPhaseTime;
                        lastPhase = phase;
                    }
                    return TeaVMProgressFeedback.CONTINUE;
                }

                @Override
                public TeaVMProgressFeedback progressReached(int progress) {
                    return null;
                }
            });

            if (lastPhase != null) {
                System.out.println(lastPhase.name() + ": " + (System.currentTimeMillis() - lastPhaseTime) + " ms");
            }

            teavm.build(outDir, "classes.js");
            boolean hasSevere = false;
            for (Problem problem : teavm.getProblemProvider().getProblems()) {
                if (problem.getSeverity() == ProblemSeverity.ERROR) {
                    hasSevere = true;
                }
            }
            TeaVMProblemRenderer.describeProblems(teavm);

            long end = System.currentTimeMillis();
            System.out.println("TeaVM complete in " + (end - start) + " ms");

            return !hasSevere;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void createStdlib() throws IOException {
        System.setProperty("sun.boot.class.path", "/stdlib");

        File baseDir = new File("/stdlib");
        baseDir.mkdirs();
        ClassLoader loader = ClassLoader.getSystemClassLoader();

        try (ZipInputStream input = new ZipInputStream(loader.getResourceAsStream("stdlib/data.zip"))) {
            unzip(input, baseDir);
        }
    }

    private static void loadTeaVMClasslib() throws IOException {
        File baseDir = new File("/teavm-stdlib");
        baseDir.mkdirs();

        byte[] data = downloadFile("teavm-classlib.zip");
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(data))) {
            unzip(input, baseDir);
        }
    }

    private static void unzip(ZipInputStream input, File toDir) throws IOException {
        while (true) {
            ZipEntry entry = input.getNextEntry();
            if (entry == null) {
                break;
            }
            if (entry.isDirectory()) {
                continue;
            }

            File outputFile = new File(toDir, entry.getName());
            outputFile.getParentFile().mkdirs();
            try (OutputStream output = new FileOutputStream(outputFile)) {
                copy(input, output);
            }
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        while (true) {
            int bytesRead = input.read(buffer);
            if (bytesRead < 0) {
                break;
            }
            output.write(buffer, 0, bytesRead);
        }
    }

    @Async
    private static native byte[] downloadFile(String url);

    private static void downloadFile(String url, AsyncCallback<byte[]> callback) {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open("GET", url, true);
        xhr.setResponseType("arraybuffer");
        xhr.onComplete(() -> {
            ArrayBuffer buffer = (ArrayBuffer) xhr.getResponse();
            Int8Array jsArray = Int8Array.create(buffer);
            byte[] array = new byte[jsArray.getLength()];
            for (int i = 0; i < array.length; ++i) {
                array[i] = jsArray.get(i);
            }
            callback.complete(array);
        });
        xhr.send();
    }

    private static void createSourceFile(String content) throws IOException {
        File file = new File("/Hello.java");

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.write(content);
        }
    }
}
