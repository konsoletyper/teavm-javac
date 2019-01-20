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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.classlib.impl.JCLPlugin;
import org.teavm.diagnostics.Problem;
import org.teavm.diagnostics.ProblemSeverity;
import org.teavm.javac.protocol.CompilableObject;
import org.teavm.javac.protocol.CompilationResultMessage;
import org.teavm.javac.protocol.CompileMessage;
import org.teavm.javac.protocol.CompilerDiagnosticMessage;
import org.teavm.javac.protocol.ErrorMessage;
import org.teavm.javac.protocol.LoadStdlibMessage;
import org.teavm.javac.protocol.TeaVMDiagnosticMessage;
import org.teavm.javac.protocol.TeaVMPhaseMessage;
import org.teavm.javac.protocol.WorkerMessage;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.impl.JSOPlugin;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.model.ClassHolderSource;
import org.teavm.parsing.CompositeClassHolderSource;
import org.teavm.parsing.DirectoryClasspathClassHolderSource;
import org.teavm.platform.plugin.PlatformPlugin;
import org.teavm.vm.TeaVM;
import org.teavm.vm.TeaVMBuilder;
import org.teavm.vm.TeaVMPhase;
import org.teavm.vm.TeaVMProgressFeedback;
import org.teavm.vm.TeaVMProgressListener;

public final class Client {
    private static boolean isBusy;
    private static final String SOURCE_FILE_NAME = "Main.java";
    private static String mainClass;

    private Client() {
    }

    public static void main(String[] args) {
        Window.worker().addEventListener("message", (MessageEvent event) -> {
            handleEvent(event);
        });
    }

    private static void handleEvent(MessageEvent event) {
        WorkerMessage request = (WorkerMessage) event.getData();
        try {
            processResponse(request);
        } catch (Throwable e) {
            log("Error occurred");
            e.printStackTrace();
            Window.worker().postMessage(createErrorResponse(request, "Error occurred processing message: "
                    + e.getMessage()));
        }
    }

    private static long initializationStartTime;

    private static void processResponse(WorkerMessage request) throws Exception {
        log("Message received: " + request.getId());

        if (isBusy) {
            log("Responded busy status");
            Window.worker().postMessage(createErrorResponse(request, "Busy"));
            return;
        }

        isBusy = true;
        switch (request.getCommand()) {
            case "load-classlib":
                init(request, ((LoadStdlibMessage) request).getUrl(), success -> {
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

    private static void compileAll(CompileMessage request) throws IOException {
        createSourceFile(request.getText());

        CompilationResultMessage response = createMessage();
        response.setId(request.getId());
        response.setCommand("compilation-complete");

        if (doCompile(request) && detectMainClass(request) && generateJavaScript(request)) {
            response.setStatus("successful");
            response.setScript(readResultingFile());
        } else {
            response.setStatus("errors");
        }

        Window.worker().postMessage(response);
    }

    private static void respondOk(WorkerMessage message) {
        WorkerMessage response = createMessage();
        response.setCommand("ok");
        response.setId(message.getId());
        Window.worker().postMessage(response);
    }

    private static <T extends ErrorMessage> T createErrorResponse(WorkerMessage request, String text) {
        T message = createMessage();
        message.setId(request.getId());
        message.setCommand("error");
        message.setText(text);
        return message;
    }

    @JSBody(script = "return {};")
    static native <T extends JSObject> T createMessage();

    private static void init(WorkerMessage request, String url, Consumer<Boolean> next) {
        log("Initializing");

        initializationStartTime = System.currentTimeMillis();
        loadTeaVMClasslib(request, url, success -> {
            if (success) {
                try {
                    createStdlib();
                } catch (IOException e) {
                    Window.worker().postMessage(createErrorResponse(request, "Error creating stdlib: "
                            + e.getMessage()));
                    log("Error initializing stdlib: " + e.getMessage());
                    success = false;
                }

                if (success) {
                    long end = System.currentTimeMillis();
                    log("Initialized in " + (end - initializationStartTime) + " ms");
                }
            }
            next.accept(success);
        });
    }

    private static boolean doCompile(WorkerMessage request) throws IOException {
        JavaCompiler compiler = JavacTool.create();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                Arrays.asList(new File("/" + SOURCE_FILE_NAME)));
        OutputStreamWriter out = new OutputStreamWriter(System.out);

        File outDir = new File("/out");
        if (outDir.exists()) {
            removeDirectory(outDir);
        }
        new File("/out").mkdirs();
        JavaCompiler.CompilationTask task = compiler.getTask(out, fileManager, createDiagnosticListener(request),
                Arrays.asList("-verbose", "-d", "/out", "-Xlint:all"), null, compilationUnits);
        boolean result = task.call();
        out.flush();

        return result;
    }

    private static DiagnosticListener<? super JavaFileObject> createDiagnosticListener(WorkerMessage request) {
        return diagnostic -> {
            CompilerDiagnosticMessage response = createMessage();
            response.setCommand("compiler-diagnostic");
            response.setId(request.getId());

            response.setKind(diagnostic.getKind().name());

            CompilableObject object = createMessage();
            object.setKind(diagnostic.getSource().getKind().name());
            object.setName(diagnostic.getSource().getName());
            response.setObject(object);

            response.setStartPosition((int) diagnostic.getStartPosition());
            response.setPosition((int) diagnostic.getPosition());
            response.setEndPosition((int) diagnostic.getEndPosition());

            response.setLineNumber((int) diagnostic.getColumnNumber());
            response.setColumnNumber((int) diagnostic.getColumnNumber());

            response.setCode(diagnostic.getCode());
            response.setMessage(diagnostic.getMessage(Locale.getDefault()));

            Window.worker().postMessage(response);
        };
    }

    private static long lastPhaseTime = System.currentTimeMillis();
    private static TeaVMPhase lastPhase;
    private static ClassHolderSource stdlibClassSource;

    static {
        Properties stdlibMapping = new Properties();
        stdlibMapping.setProperty("packagePrefix.java", "org.teavm.classlib");
        stdlibMapping.setProperty("classPrefix.java", "T");

        stdlibClassSource = new DirectoryClasspathClassHolderSource(new File("/teavm-stdlib"), stdlibMapping);
    }

    private static boolean detectMainClass(WorkerMessage request) throws IOException {
        Set<String> candidates = new HashSet<>();
        detectMainClass(new File("/out"), candidates);
        if (candidates.size() != 1) {
            String text = candidates.isEmpty() ? "Main method not found" : "Multiple main methods found";
            TeaVMDiagnosticMessage message = createMessage();
            message.setId(request.getId());
            message.setCommand("diagnostic");
            message.setSeverity("ERROR");
            message.setText(text);
            message.setFileName(null);
            Window.worker().postMessage(message);
            return false;
        }

        mainClass = candidates.iterator().next();
        mainClass = mainClass.replace('/', '.');
        return true;
    }

    private static boolean generateJavaScript(WorkerMessage request) {
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

            long pluginInstallationStart = System.currentTimeMillis();
            new JSOPlugin().install(teavm);
            new PlatformPlugin().install(teavm);
            new JCLPlugin().install(teavm);
            log("Plugins loaded in " + (System.currentTimeMillis() - pluginInstallationStart) + " ms");

            teavm.entryPoint(mainClass);
            File outDir = new File("/js-out");
            outDir.mkdirs();

            log("TeaVM initialized in " + (System.currentTimeMillis() - start) + " ms");

            lastPhase = null;
            lastPhaseTime = System.currentTimeMillis();
            teavm.setProgressListener(new TeaVMProgressListener() {
                @Override
                public TeaVMProgressFeedback phaseStarted(TeaVMPhase phase, int count) {
                    if (phase != lastPhase) {
                        reportPhase(request, phase);
                        long newPhaseTime = System.currentTimeMillis();
                        if (lastPhase != null) {
                            log(lastPhase.name() + ": " + (newPhaseTime - lastPhaseTime) + " ms");
                        }
                        lastPhaseTime = newPhaseTime;
                        lastPhase = phase;
                    }
                    return TeaVMProgressFeedback.CONTINUE;
                }

                @Override
                public TeaVMProgressFeedback progressReached(int progress) {
                    return TeaVMProgressFeedback.CONTINUE;
                }
            });

            if (lastPhase != null) {
                log(lastPhase.name() + ": " + (System.currentTimeMillis() - lastPhaseTime) + " ms");
            }

            teavm.build(outDir, "classes.js");
            boolean hasSevere = false;
            for (Problem problem : teavm.getProblemProvider().getProblems()) {
                if (problem.getSeverity() == ProblemSeverity.ERROR) {
                    hasSevere = true;
                }
            }
            TeaVMProblemRenderer.describeProblems(request, SOURCE_FILE_NAME, teavm);

            long end = System.currentTimeMillis();
            log("TeaVM complete in " + (end - start) + " ms");

            return !hasSevere;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void reportPhase(WorkerMessage request, TeaVMPhase phase) {
        TeaVMPhaseMessage phaseMessage = createMessage();
        phaseMessage.setId(request.getId());
        phaseMessage.setCommand("phase");
        phaseMessage.setPhase(phase.name());
        Window.worker().postMessage(phaseMessage);
    }

    private static void loadTeaVMClasslib(WorkerMessage request, String url, Consumer<Boolean> next) {
        File baseDir = new File("/teavm-stdlib");
        baseDir.mkdirs();

        downloadFile(url, data -> {
            boolean success;
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(data))) {
                unzip(input, baseDir);
                success = true;
            } catch (IOException e) {
                success = false;
                Window.worker().postMessage(createErrorResponse(request, "Error occurred downloading classlib: "
                        + e.getMessage()));
            }
            next.accept(success);
        });
    }

    private static void createStdlib() throws IOException {
        System.setProperty("sun.boot.class.path", "/stdlib");

        File baseDir = new File("/stdlib");
        baseDir.mkdirs();
        traverseStdlib(new File("/teavm-stdlib"), baseDir, ".");
    }

    private static void traverseStdlib(File sourceDir, File destDir, String path) throws IOException {
        File sourceFile = new File(sourceDir, path);
        File[] files = sourceFile.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                traverseStdlib(sourceDir, destDir, path + "/" + file.getName());
            } else if (file.getName().endsWith(".class")) {
                transformClassFile(file, destDir);
            }
        }
    }

    private static void transformClassFile(File sourceFile, File destDir) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        StdlibConverter converter = new StdlibConverter(writer);
        try (InputStream input = new FileInputStream(sourceFile)) {
            ClassReader reader = new ClassReader(input);
            reader.accept(converter, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        }
        if (converter.visible) {
            File destFile = new File(destDir, converter.className + ".class");
            destFile.getParentFile().mkdirs();
            try (OutputStream output = new FileOutputStream(destFile)) {
                output.write(writer.toByteArray());
            }
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

    private static void downloadFile(String url, Consumer<byte[]> callback) {
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
            callback.accept(array);
        });
        xhr.send();
    }

    private static void createSourceFile(String content) throws IOException {
        File file = new File("/" + SOURCE_FILE_NAME);

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.write(content);
        }
    }

    private static void removeDirectory(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                removeDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }

    private static void detectMainClass(File dir, Set<String> mainClasses) throws IOException {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                detectMainClass(file, mainClasses);
            } else if (file.getName().endsWith(".class")) {
                MainMethodFinder finder = new MainMethodFinder();
                try (InputStream input = new FileInputStream(file)) {
                    ClassReader reader = new ClassReader(input);
                    reader.accept(finder, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                }
                if (finder.className != null && finder.hasMainMethod) {
                    mainClasses.add(finder.className);
                }
            }
        }
    }

    private static void log(String message) {
        System.out.println(message);
    }

    private static String readResultingFile() throws IOException {
        File jsFile = new File("/js-out/classes.js");
        if (!jsFile.exists()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsFile), "UTF-8"))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
