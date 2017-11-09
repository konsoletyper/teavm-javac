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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.*;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.InvokeInstruction;

public class ConcurrentHashMapElimination implements ClassHolderTransformer {
    private static final Set<String> EXPECTED_CLASSES = new HashSet<>(Arrays.asList(
            ConcurrentHashMap.class.getName(), WeakHashMap.class.getName()));
    private static final String REPLACEMENT = HashMap.class.getName();

    @Override
    public void transformClass(ClassHolder cls, ClassReaderSource innerSource, Diagnostics diagnostics) {
        for (MethodHolder method : cls.getMethods()) {
            if (method.getProgram() != null) {
                transformProgram(method.getProgram());
            }
        }
    }

    private void transformProgram(Program program) {
        for (BasicBlock block : program.getBasicBlocks()) {
            for (Instruction instruction : block) {
                if (instruction instanceof ConstructInstruction) {
                    ConstructInstruction construct = (ConstructInstruction) instruction;
                    if (EXPECTED_CLASSES.contains(construct.getType())) {
                        construct.setType(REPLACEMENT);
                    }
                } else if (instruction instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instruction;
                    if (EXPECTED_CLASSES.contains(invoke.getMethod().getClassName())) {
                        invoke.setMethod(new MethodReference(REPLACEMENT, invoke.getMethod().getDescriptor()));
                    }
                }
            }
        }
    }
}
