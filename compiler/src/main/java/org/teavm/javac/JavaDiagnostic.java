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

import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

public class JavaDiagnostic extends BaseDiagnostic {
    private final Diagnostic<? extends JavaFileObject> innerDiagnostic;

    public JavaDiagnostic(Diagnostic<? extends JavaFileObject> innerDiagnostic) {
        this.innerDiagnostic = innerDiagnostic;
    }

    @Override
    @JSExport
    @JSProperty
    public String getType() {
        return "javac";
    }

    @Override
    @JSExport
    @JSProperty
    public String getSeverity() {
        return switch (innerDiagnostic.getKind()) {
            case ERROR -> "error";
            case WARNING -> "warning";
            default -> "other";
        };
    }

    @Override
    @JSExport
    @JSProperty
    public String getMessage() {
        return innerDiagnostic.getMessage(Locale.ENGLISH);
    }

    @Override
    @JSExport
    @JSProperty
    public int getLineNumber() {
        return (int) innerDiagnostic.getLineNumber();
    }

    @JSExport
    @JSProperty
    public int getColumnNumber() {
        return (int) innerDiagnostic.getColumnNumber();
    }

    @Override
    @JSExport
    @JSProperty
    public String getFileName() {
        return innerDiagnostic.getSource().getName();
    }

    @JSExport
    @JSProperty
    public int getStartPosition() {
        return (int) innerDiagnostic.getStartPosition();
    }

    @JSExport
    @JSProperty
    public int getPosition() {
        return (int) innerDiagnostic.getPosition();
    }

    @JSExport
    @JSProperty
    public int getEndPosition() {
        return (int) innerDiagnostic.getEndPosition();
    }
}
