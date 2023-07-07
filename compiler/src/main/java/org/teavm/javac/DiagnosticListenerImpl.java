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

import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

class DiagnosticListenerImpl implements DiagnosticListener<JavaFileObject> {
    private final List<Compiler.DiagnosticListenerRegistration> regs;

    DiagnosticListenerImpl(List<Compiler.DiagnosticListenerRegistration> regs) {
        this.regs = regs;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        if (regs.isEmpty()) {
            return;
        }
        var wrapper = new JavaDiagnostic(diagnostic);
        for (var reg : regs) {
            reg.listener.onDiagnostic(wrapper);
        }
    }
}
