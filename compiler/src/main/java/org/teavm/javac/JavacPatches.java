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

import java.lang.reflect.Method;
import java.util.Optional;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.*;
import org.teavm.model.instructions.*;
import org.teavm.model.optimization.ConstantConditionElimination;
import org.teavm.model.optimization.GlobalValueNumbering;
import org.teavm.model.optimization.UnreachableBasicBlockElimination;

public class JavacPatches implements ClassHolderTransformer {
    @Override
    public void transformClass(ClassHolder cls, ClassReaderSource innerSource, Diagnostics diagnostics) {
        switch (cls.getName()) {
            case "com.sun.tools.javac.main.JavaCompiler":
                processJavaCompiler(cls);
                break;
            case "com.sun.tools.javac.processing.JavacProcessingEnvironment":
                processJavacProcessingEnvironment(cls);
                break;
            case "com.sun.tools.javac.main.Main":
                processMain(cls);
                break;
            case "com.sun.tools.javac.file.RegularFileObject":
                processRegularFileObject(cls);
                break;
            case "org.mozilla.javascript.Kit":
                processKit(cls);
                break;
        }
    }

    private void processJavaCompiler(ClassHolder cls) {
        MethodHolder method = cls.getMethod(new MethodDescriptor("initProcessAnnotations", Iterable.class, void.class));
        if (method != null) {
            Program newProgram = new Program();
            newProgram.createVariable();
            newProgram.createVariable();
            BasicBlock block = newProgram.createBasicBlock();
            block.add(new ExitInstruction());
            method.setProgram(newProgram);
        }
    }

    private void processJavacProcessingEnvironment(ClassHolder cls) {
        MethodHolder method = cls.getMethod(new MethodDescriptor("initProcessorClassLoader", void.class));
        if (method != null) {
            Program newProgram = new Program();
            newProgram.createVariable();
            BasicBlock block = newProgram.createBasicBlock();
            block.add(new ExitInstruction());
            method.setProgram(newProgram);
        }
    }

    private void processMain(ClassHolder cls) {
        MethodHolder method = cls.getMethod(new MethodDescriptor("showClass", String.class, void.class));
        if (method != null) {
            Program newProgram = new Program();
            newProgram.createVariable();
            newProgram.createVariable();
            BasicBlock block = newProgram.createBasicBlock();
            block.add(new ExitInstruction());
            method.setProgram(newProgram);
        }
    }

    private void processKit(ClassHolder cls) {
        MethodHolder method = cls.getMethod(new MethodDescriptor("classOrNull", String.class, Class.class));
        if (method != null) {
            Program newProgram = new Program();
            newProgram.createVariable();
            newProgram.createVariable();
            Variable nullVar = newProgram.createVariable();
            BasicBlock block = newProgram.createBasicBlock();

            NullConstantInstruction nullConstant = new NullConstantInstruction();
            nullConstant.setReceiver(nullVar);
            block.add(nullConstant);

            ExitInstruction exit = new ExitInstruction();
            exit.setValueToReturn(nullVar);
            block.add(exit);

            method.setProgram(newProgram);
        }

        method = cls.getMethod(new MethodDescriptor("<clinit>", void.class));
        if (method != null) {
            Program newProgram = new Program();
            newProgram.createVariable();
            Variable nullVar = newProgram.createVariable();

            BasicBlock block = newProgram.createBasicBlock();

            NullConstantInstruction nullConstant = new NullConstantInstruction();
            nullConstant.setReceiver(nullVar);
            block.add(nullConstant);

            PutFieldInstruction putField = new PutFieldInstruction();
            putField.setFieldType(ValueType.parse(Method.class));
            putField.setField(new FieldReference(cls.getName(), "Throwable_initCause"));
            putField.setValue(nullVar);
            block.add(putField);

            block.add(new ExitInstruction());
            method.setProgram(newProgram);
        }
    }

    private void processRegularFileObject(ClassHolder cls) {
        Optional<MethodHolder> method = cls.getMethods().stream()
                .filter(m -> m.getName().equals("isNameCompatible"))
                .findFirst();
        if (method.isPresent()) {
            Program program = method.get().getProgram();
            for (BasicBlock block : program.getBasicBlocks()) {
                for (Instruction insn : block) {
                    if (insn instanceof GetFieldInstruction) {
                        GetFieldInstruction getField = (GetFieldInstruction) insn;
                        if (getField.getField().getFieldName().equals("isMacOS")) {
                            IntegerConstantInstruction intConstant = new IntegerConstantInstruction();
                            intConstant.setConstant(0);
                            intConstant.setReceiver(getField.getReceiver());
                            intConstant.setLocation(getField.getLocation());
                            getField.replace(intConstant);
                        }
                    }
                }
            }

            boolean changed;
            do {
                changed = new GlobalValueNumbering(true).optimize(program)
                        | new ConstantConditionElimination().optimize(null, program)
                        | new UnreachableBasicBlockElimination().optimize(null, program);
            } while (changed);
        }
    }
}
