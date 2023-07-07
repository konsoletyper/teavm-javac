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

import java.lang.annotation.Annotation;
import java.util.stream.Stream;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

public class Patches implements TeaVMPlugin, ClassHolderTransformer {
    @Override
    public void install(TeaVMHost host) {
        host.add(this);
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        switch (cls.getName()) {
            case "com.sun.tools.javac.model.AnnotationProxyMaker":
                transformAnnotationProxyMaker(cls, context);
                break;
            case "com.sun.tools.javac.api.BasicJavacTask":
                transformBasicJavacTask(cls, context);
                break;
            case "com.sun.tools.javac.main.JavaCompiler":
                transformJavaCompiler(cls, context);
                break;
            case "com.sun.tools.javac.file.BaseFileManager":
                transformBaseFileManager(cls, context);
                break;
            case "com.sun.tools.javac.comp.Modules":
                transformModules(cls, context);
                break;
            case "java.lang.System":
                transformSystem(cls, context);
                break;
        }
    }

    private void transformAnnotationProxyMaker(ClassHolder cls, ClassHolderTransformerContext context) {
        var method = cls.getMethod(new MethodDescriptor("generateAnnotation",
                ValueType.object("com.sun.tools.javac.code.Attribute$Compound"),
                ValueType.object("java.lang.Class"), ValueType.object("java.lang.annotation.Annotation")));
        var pe = ProgramEmitter.create(method, context.getHierarchy());
        pe.constantNull(Annotation.class).returnValue();
    }

    private void transformBasicJavacTask(ClassHolder cls, ClassHolderTransformerContext context) {
        var method = cls.getMethod(new MethodDescriptor("initPlugins", ValueType.object("java.util.Set"),
                ValueType.VOID));
        var pe = ProgramEmitter.create(method, context.getHierarchy());
        pe.exit();

        method = cls.getMethod(new MethodDescriptor("initDocLint", ValueType.object("com.sun.tools.javac.util.List"),
                ValueType.VOID));
        pe = ProgramEmitter.create(method, context.getHierarchy());
        pe.exit();
    }

    private void transformJavaCompiler(ClassHolder cls, ClassHolderTransformerContext context) {
        var method = cls.getMethod(new MethodDescriptor("initProcessAnnotations",
                ValueType.object("java.lang.Iterable"), ValueType.object("java.util.Collection"),
                ValueType.object("java.util.Collection"), ValueType.VOID));
        var pe = ProgramEmitter.create(method, context.getHierarchy());
        pe.exit();

        method = cls.getMethod(new MethodDescriptor("<init>", ValueType.object("com.sun.tools.javac.util.Context"),
                ValueType.VOID));
        for (var block : method.getProgram().getBasicBlocks()) {
            for (var instruction : block) {
                if (instruction instanceof InvokeInstruction invoke) {
                    if (invoke.getMethod().getClassName().equals("com.sun.tools.javac.file.JavacFileManager")
                            && invoke.getMethod().getName().equals("preRegister")) {
                        invoke.delete();
                    }
                }
            }
        }
    }

    private void transformBaseFileManager(ClassHolder cls, ClassHolderTransformerContext context) {
        var method = cls.getMethod(new MethodDescriptor("deferredClose", ValueType.VOID));
        var pe = ProgramEmitter.create(method, context.getHierarchy());
        pe.exit();
    }

    private void transformModules(ClassHolder cls, ClassHolderTransformerContext context) {
        var method = cls.getMethod(new MethodDescriptor("filterAlreadyWarnedIncubatorModules",
                ValueType.object("java.util.stream.Stream"), ValueType.object("java.util.stream.Stream")));
        var pe = ProgramEmitter.create(method, context.getHierarchy());
        pe.var(1, Stream.class).returnValue();
    }

    private void transformSystem(ClassHolder cls, ClassHolderTransformerContext context) {
        var method = new MethodHolder(new MethodDescriptor("exit", ValueType.INTEGER, ValueType.VOID));
        cls.addMethod(method);
        var pe = ProgramEmitter.create(method, context.getHierarchy());
        pe.construct(RuntimeException.class).raise();
    }
}
