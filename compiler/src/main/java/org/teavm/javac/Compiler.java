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

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import org.objectweb.asm.ClassReader;
import org.teavm.compiler.bootstrap.ArchiveReader;
import org.teavm.compiler.bootstrap.ByteArrayResourceProvider;
import org.teavm.compiler.bootstrap.CompileCallback;
import org.teavm.compiler.bootstrap.TeaVMBootstrapCompiler;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSExport;
import org.teavm.jso.core.JSBoolean;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.vm.MemoryBuildTarget;
import static com.sun.tools.javac.comp.CompileStates.CompileState;

@JSClass(name = "Compiler")
public final class Compiler {
    private final Map<String, FileData> sourceFiles = new LinkedHashMap<>();
    private final Map<String, FileData> classFiles = new LinkedHashMap<>();
    private final Map<String, FileData> sdkFiles = new LinkedHashMap<>();
    private final Map<String, FileData> outputFiles = new LinkedHashMap<>();
    private final MemoryBuildTarget wasmOutput = new MemoryBuildTarget();
    private SimpleJavaCompiler compiler;
    private List<DiagnosticListenerRegistration> diagnosticListeners = new ArrayList<>();
    private ByteArrayResourceProvider teavmClasslib;
    private TeaVMBootstrapCompiler teavmCompiler;
    private boolean wasmCompilationInProgress;

    Compiler() {
    }

    @JSExport
    public void addSourceFile(String name, String content) {
        addFile(sourceFiles, name, content.getBytes(StandardCharsets.UTF_8));
    }

    @JSExport
    public void clearSourceFiles() {
        sourceFiles.clear();
    }

    @JSExport
    public void addClassFile(String name, Int8Array content) {
        addFile(classFiles, name, content);
    }

    @JSExport
    public void addOutputClassFile(String name, Int8Array content) {
        addFile(outputFiles, name, content);
    }

    @JSExport
    public void addOutputJarFile(String name, Int8Array content) throws IOException {
        addJarFile(outputFiles, content);
    }

    @JSExport
    public void addJarFile(Int8Array content) throws IOException {
        addJarFile(classFiles, content);
    }

    private void addJarFile(Map<String, FileData> target, Int8Array content) throws IOException {
        try (var input = new ZipInputStream(new ByteArrayInputStream(content.copyToJavaArray()))) {
            while (true) {
                var entry = input.getNextEntry();
                if (entry == null) {
                    break;
                }
                addFile(target, entry.getName(), input.readAllBytes());
            }
        }
    }

    @JSExport
    public void setSdk(Int8Array content) throws IOException {
        try (var input = new ArchiveReader(new ByteArrayInputStream(content.copyToJavaArray()))) {
            while (true) {
                var entry = input.readNext();
                if (entry == null) {
                    break;
                }
                var file = addFile(sdkFiles, entry, input.readData());
                file.isJavaBase = true;
            }
        }
    }

    @JSExport
    public void setTeaVMClasslib(Int8Array content) throws IOException {
        teavmClasslib = ByteArrayResourceProvider.fromArchive(new ByteArrayInputStream(content.copyToJavaArray()));
        teavmCompiler = null;
    }

    @JSExport
    public void clearInputClassFiles() {
        classFiles.clear();
    }

    @JSExport
    public void clearOutputFiles() {
        outputFiles.clear();
    }

    @JSExport
    public Int8Array getOutputFile(String name) {
        var data = outputFiles.get(name);
        if (data == null) {
            return null;
        }
        return Int8Array.copyFromJavaArray(data.data);
    }

    @JSExport
    public String[] listOutputFiles() {
        return outputFiles.keySet().toArray(String[]::new);
    }

    @JSExport
    public Int8Array getOutputJar() throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var file : outputFiles.values()) {
                zip.putNextEntry(new ZipEntry(file.path));
                zip.write(file.data);
                zip.closeEntry();
            }
        }
        return Int8Array.copyFromJavaArray(output.toByteArray());
    }


    @JSExport
    public Int8Array getWebAssemblyOutputFile(String name) {
        var data = wasmOutput.getContent(name);
        if (data == null) {
            return null;
        }
        return Int8Array.copyFromJavaArray(data);
    }

    @JSExport
    public String[] listWebAssemblyOutputFiles() {
        return wasmOutput.getNames().toArray(String[]::new);
    }

    @JSExport
    public Int8Array getWebAssemblyOutputArchive() throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var name : wasmOutput.getNames()) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(wasmOutput.getContent(name));
                zip.closeEntry();
            }
        }
        return Int8Array.copyFromJavaArray(output.toByteArray());
    }

    private FileData addFile(Map<String, FileData> files, String name, Int8Array content) {
        return addFile(files, name, content.copyToJavaArray());
    }

    private FileData addFile(Map<String, FileData> files, String name, byte[] content) {
        var data = new FileData(files);
        data.lastModified = System.currentTimeMillis();
        data.path = name;
        data.data = content;
        files.put(name, data);
        return data;
    }

    @JSExport
    public boolean compile() {
        initCompiler();
        try {
            return compiler.simpleCompile();
        } finally {
            compiler = null;
        }
    }

    @JSExport
    public String[] detectMainClasses() throws IOException {
        var mainClasses = new ArrayList<String>();
        for (var file : outputFiles.values()) {
            if (file.getName().endsWith(".class")) {
                var finder = new MainMethodFinder();
                try (var input = file.openInputStream()) {
                    var reader = new ClassReader(input);
                    reader.accept(finder, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                }
                if (finder.className != null && finder.hasMainMethod) {
                    mainClasses.add(finder.className);
                }
            }
        }
        return mainClasses.toArray(new String[0]);
    }

    @JSExport
    public JSPromise<JSBoolean> generateWebAssembly(WebAssemblyCompilationOptions options) {
        var outputName = options.getOutputName() != null && !JSObjects.isUndefined(options.getOutputName())
                ? options.getOutputName().stringValue()
                : "app";
        if (JSObjects.isUndefined(options.getMainClass()) || options.getMainClass() == null) {
            throw new IllegalArgumentException("Main class not specified");
        }
        var mainClass = options.getMainClass().stringValue();
        var strictMode = JSObjects.isUndefined(options.getStrictMode()) || options.getStrictMode().booleanValue();
        var obfuscated = JSObjects.isUndefined(options.getObfuscated()) || options.getObfuscated().booleanValue();
        var assertionsRemoved = !JSObjects.isUndefined(options.getAssertionsRemoved())
                && options.getAssertionsRemoved().booleanValue();
        var asyncEnabled = JSObjects.isUndefined(options.getAsyncEnabled())
                || options.getAsyncEnabled().booleanValue();

        if (teavmClasslib == null) {
            throw new IllegalStateException("TeaVM class library not set");
        }
        if (wasmCompilationInProgress) {
            throw new IllegalStateException("WebAssembly generation is already in progress");
        }
        if (teavmCompiler == null) {
            teavmCompiler = new TeaVMBootstrapCompiler();
            teavmCompiler.setClasslibResourceProvider(teavmClasslib);
            teavmCompiler.setClassesResourceProvider(new MemoryResourceProvider(List.of(classFiles, outputFiles)));
            teavmCompiler.setDebugInfo(true);
        }
        teavmCompiler.setMainClass(mainClass);
        teavmCompiler.setOutputName(outputName);
        teavmCompiler.setStrictMode(strictMode);
        teavmCompiler.setObfuscated(obfuscated);
        teavmCompiler.setAssertionsRemoved(assertionsRemoved);
        teavmCompiler.setAsyncEnabled(asyncEnabled);
        teavmCompiler.setBuildTarget(wasmOutput);
        wasmCompilationInProgress = true;
        return new JSPromise<>((resolveFunc, rejectFunc) -> {
            try {
                teavmCompiler.compile(new CompileCallback() {
                    @Override
                    public void complete() {
                        try {
                            var success = reportTeaVMProblems();
                            wasmCompilationInProgress = false;
                            resolveFunc.accept(JSBoolean.valueOf(success));
                        } catch (Throwable e) {
                            wasmCompilationInProgress = false;
                            rejectFunc.accept(e);
                        }
                    }

                    @Override
                    public void error(Throwable e) {
                        wasmCompilationInProgress = false;
                        rejectFunc.accept(e);
                    }
                });
            } catch (Throwable e) {
                wasmCompilationInProgress = false;
                rejectFunc.accept(e);
            }
        });
    }

    private boolean reportTeaVMProblems() {
        var problemProvider = teavmCompiler.getProblemProvider();
        if (!diagnosticListeners.isEmpty()) {
            for (var problem : problemProvider.getProblems()) {
                var wrapper = new TeaVMDiagnostic(problem);
                for (var reg : List.copyOf(diagnosticListeners)) {
                    reg.listener.onDiagnostic(wrapper);
                }
            }
        }
        return problemProvider.getSevereProblems().isEmpty();
    }

    @JSExport
    public ListenerRegistration onDiagnostic(CompilerDiagnosticListener diagnosticListener) {
        var reg = new DiagnosticListenerRegistration(diagnosticListeners, diagnosticListener);
        diagnosticListeners.add(reg);
        return reg;
    }

    private void initCompiler() {
        if (compiler != null) {
            return;
        }
        var context = new Context();
        context.put(DiagnosticListener.class, new DiagnosticListenerImpl(diagnosticListeners));
        var fileManager = new FileManagerImpl(sourceFiles, classFiles, sdkFiles, outputFiles);
        context.put(JavaFileManager.class, fileManager);
        compiler = new SimpleJavaCompiler(context);
        compiler.prepare();
    }

    private class SimpleJavaCompiler extends JavaCompiler {
        SimpleJavaCompiler(Context context) {
            super(context);
        }

        void prepare() {
            syms.unnamedModule.visiblePackages = new LinkedHashMap<>();
            syms.unnamedModule.readModules = Set.of(syms.unnamedModule, syms.java_base);
            syms.java_base.complete();
            for (var export : syms.java_base.exports) {
                syms.unnamedModule.visiblePackages.put(export.packge.fullname, export.packge);
            }
        }

        boolean simpleCompile() {
            var files = sourceFiles.values().stream().map(x -> (JavaFileObject) x).toList();
            var units = stopIfError(CompileState.ENTER, parseFiles(files));
            enterTrees(stopIfError(CompileState.ENTER, initModules(units)));
            generate(desugar(flow(attribute(todo))));
            return log.nerrors == 0;
        }
    }

    static class DiagnosticListenerRegistration extends ListenerRegistration {
        private final List<DiagnosticListenerRegistration> listeners;
        CompilerDiagnosticListener listener;

        DiagnosticListenerRegistration(List<DiagnosticListenerRegistration> listeners,
                CompilerDiagnosticListener listener) {
            this.listeners = listeners;
            this.listener = listener;
        }

        @Override
        public void destroy() {
            listeners.remove(this);
        }
    }
}
