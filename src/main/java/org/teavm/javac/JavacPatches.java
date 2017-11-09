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

import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.*;
import org.teavm.model.instructions.ExitInstruction;

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
}
