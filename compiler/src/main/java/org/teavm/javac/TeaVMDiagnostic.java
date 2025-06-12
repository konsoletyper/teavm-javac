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

import org.teavm.diagnostics.Problem;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class TeaVMDiagnostic extends BaseDiagnostic {
    private Problem problem;

    public TeaVMDiagnostic(Problem problem) {
        this.problem = problem;
    }

    @Override
    @JSExport
    @JSProperty
    public String getType() {
        return "teavm";
    }

    @Override
    @JSExport
    @JSProperty
    public String getSeverity() {
        return switch (problem.getSeverity()) {
            case ERROR -> "error";
            case WARNING -> "warning";
        };
    }

    @Override
    @JSExport
    @JSProperty
    public String getMessage() {
        return problem.getText();
    }

    @Override
    @JSExport
    @JSProperty
    public int getLineNumber() {
        if (problem.getLocation().getSourceLocation() == null) {
            return -1;
        }
        return problem.getLocation().getSourceLocation().getLine();
    }

    @Override
    @JSExport
    @JSProperty
    public String getFileName() {
        if (problem.getLocation().getSourceLocation() == null) {
            return null;
        }
        return problem.getLocation().getSourceLocation().getFileName();
    }
}
