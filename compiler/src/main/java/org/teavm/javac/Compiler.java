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
import org.teavm.backend.wasm.WasmDebugInfoLocation;
import org.teavm.backend.wasm.WasmGCTarget;
import org.teavm.classlib.impl.JCLPlugin;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSExport;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.impl.JSOPlugin;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.parsing.CompositeClassHolderSource;
import org.teavm.parsing.resource.CompositeResourceProvider;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.platform.plugin.PlatformPlugin;
import org.teavm.vm.TeaVMBuilder;
import org.teavm.vm.TeaVMOptimizationLevel;
import static com.sun.tools.javac.comp.CompileStates.CompileState;

@JSClass(name = "Compiler")
public final class Compiler {
    private final Map<String, FileData> sourceFiles = new LinkedHashMap<>();
    private final Map<String, FileData> classFiles = new LinkedHashMap<>();
    private final Map<String, FileData> sdkFiles = new LinkedHashMap<>();
    private final Map<String, FileData> teavmClasslibFiles = new LinkedHashMap<>();
    private final Map<String, FileData> outputFiles = new LinkedHashMap<>();
    private final Map<String, FileData> wasmOutputFiles = new LinkedHashMap<>();
    private SimpleJavaCompiler compiler;
    private List<DiagnosticListenerRegistration> diagnosticListeners = new ArrayList<>();
    private ResourceProvider resourceProvider;
    private ClassHolderSource classSource;

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
    public void addJarFile(Int8Array content) throws IOException {
        try (var input = new ZipInputStream(new ByteArrayInputStream(content.copyToJavaArray()))) {
            while (true) {
                var entry = input.getNextEntry();
                if (entry == null) {
                    break;
                }
                addFile(classFiles, entry.getName(), input.readAllBytes());
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
        try (var input = new ArchiveReader(new ByteArrayInputStream(content.copyToJavaArray()))) {
            while (true) {
                var entry = input.readNext();
                if (entry == null) {
                    break;
                }
                addFile(teavmClasslibFiles, entry, input.readData());
            }
        }
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
        var data = wasmOutputFiles.get(name);
        if (data == null) {
            return null;
        }
        return Int8Array.copyFromJavaArray(data.data);
    }

    @JSExport
    public String[] listWebAssemblyOutputFiles() {
        return wasmOutputFiles.keySet().toArray(String[]::new);
    }

    @JSExport
    public Int8Array getWebAssemblyOutputArchive() throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var file : wasmOutputFiles.values()) {
                zip.putNextEntry(new ZipEntry(file.path));
                zip.write(file.data);
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
    public boolean generateWebAssembly(WebAssemblyCompilationOptions options) {
        var outputName = options.getOutputName() != null && !JSObjects.isUndefined(options.getOutputName())
                ? options.getOutputName().stringValue()
                : "app";
        if (JSObjects.isUndefined(options.getMainClass()) || options.getMainClass() == null) {
            throw new IllegalArgumentException("Main class not specified");
        }
        var mainClass = options.getMainClass().stringValue();

        var target = new WasmGCTarget();
        var refCache = new ReferenceCache();
        if (classSource == null) {
            resourceProvider = new MemoryResourceProvider(List.of(teavmClasslibFiles, outputFiles));
            classSource = new ClasspathClassHolderSource(resourceProvider, refCache);
        }
        var currentResourceProvider = new CompositeResourceProvider(new MemoryResourceProvider(List.of(outputFiles)),
                resourceProvider);
        var currentClassSource = new CompositeClassHolderSource(List.of(
                new ClasspathClassHolderSource(currentResourceProvider, refCache), classSource));
        var teavm = new TeaVMBuilder(target)
                .setClassSource(currentClassSource)
                .setResourceProvider(currentResourceProvider)
                .setReferenceCache(refCache)
                .setObfuscated(true)
                .setStrict(true)
                .build();
        teavm.setOptimizationLevel(TeaVMOptimizationLevel.ADVANCED);
        new JSOPlugin().install(teavm);
        new PlatformPlugin().install(teavm);
        new JCLPlugin().install(teavm);
        teavm.setEntryPoint(mainClass);
        target.setObfuscated(false);
        target.setDebugInfoLocation(WasmDebugInfoLocation.EMBEDDED);
        target.setDebugInfo(true);
        teavm.build(new MemoryBuildTarget(wasmOutputFiles), outputName);
        if (!diagnosticListeners.isEmpty()) {
            for (var problem : teavm.getProblemProvider().getProblems()) {
                var wrapper = new TeaVMDiagnostic(problem);
                for (var reg : diagnosticListeners) {
                    reg.listener.onDiagnostic(wrapper);
                }
            }
        }
        return teavm.getProblemProvider().getSevereProblems().isEmpty();
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
