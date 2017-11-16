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

public class ClassReplacementElimination implements ClassHolderTransformer {
    private static final Map<String, String> replacements = new HashMap<>();

    static {
        replacements.put(ConcurrentHashMap.class.getName(), HashMap.class.getName());
        replacements.put(WeakHashMap.class.getName(), HashMap.class.getName());
        replacements.put("com.sun.tools.javac.util.ServiceLoader", ServiceLoader.class.getName());
    }

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
                    construct.setType(replace(construct.getType()));
                } else if (instruction instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instruction;
                    invoke.setMethod(replace(invoke.getMethod()));
                }
            }
        }
    }

    private String replace(String className) {
        return replacements.getOrDefault(className, className);
    }

    private MethodReference replace(MethodReference method) {
        String className = replace(method.getClassName());
        ValueType[] newSignature = method.getSignature();
        boolean signatureChanged = false;
        for (int i = 0; i < newSignature.length; ++i) {
            ValueType type = replace(newSignature[i]);
            if (newSignature[i] != type) {
                newSignature[i] = type;
                signatureChanged = true;
            }
        }

        return !className.equals(method.getClassName()) || signatureChanged
                ? new MethodReference(className, method.getName(), newSignature)
                : method;
    }

    private ValueType replace(ValueType type) {
        if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            String newClassName = replace(className);
            return !className.equals(newClassName) ? ValueType.object(newClassName) : type;
        } else if (type instanceof ValueType.Array) {
            ValueType item = ((ValueType.Array) type).getItemType();
            ValueType newItem = replace(item);
            return newItem != item ? ValueType.arrayOf(newItem) : type;
        } else {
            return type;
        }
    }
}
