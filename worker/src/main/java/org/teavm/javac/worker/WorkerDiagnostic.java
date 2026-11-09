/*
 *  Copyright 2026 Alexey Andreev.
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

package org.teavm.javac.worker;

import org.teavm.javac.protocol.CompilerDiagnosticMessage;
import org.teavm.javac.protocol.TeaVMDiagnosticMessage;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

@JSClass(transparent = true)
public final class WorkerDiagnostic {
    private final String type;
    private final String severity;
    private final String fileName;
    private final int lineNumber;
    private final int columnNumber;
    private final int startPosition;
    private final int position;
    private final int endPosition;
    private final String message;

    private WorkerDiagnostic(String type, String severity, String fileName, int lineNumber, int columnNumber,
            int startPosition, int position, int endPosition, String message) {
        this.type = type;
        this.severity = severity;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.startPosition = startPosition;
        this.position = position;
        this.endPosition = endPosition;
        this.message = message;
    }

    static WorkerDiagnostic fromJavac(CompilerDiagnosticMessage message) {
        return new WorkerDiagnostic("javac", message.getSeverity(), message.getFileName(), message.getLineNumber(),
                message.getColumnNumber(), message.getStartPosition(), message.getPosition(),
                message.getEndPosition(), message.getMessage());
    }

    static WorkerDiagnostic fromTeaVM(TeaVMDiagnosticMessage message) {
        return new WorkerDiagnostic("teavm", message.getSeverity(), message.getFileName(), message.getLineNumber(),
                -1, -1, -1, -1, message.getMessage());
    }

    /**
     * Either `"javac"` or `"teavm"`.
     */
    @JSExport
    @JSProperty
    public String getType() {
        return type;
    }

    /**
     * Either `"error"`, `"warning"` or `"other"`.
     */
    @JSExport
    @JSProperty
    public String getSeverity() {
        return severity;
    }

    @JSExport
    @JSProperty
    public String getFileName() {
        return fileName;
    }

    @JSExport
    @JSProperty
    public int getLineNumber() {
        return lineNumber;
    }

    @JSExport
    @JSProperty
    public int getColumnNumber() {
        return columnNumber;
    }

    @JSExport
    @JSProperty
    public int getStartPosition() {
        return startPosition;
    }

    @JSExport
    @JSProperty
    public int getPosition() {
        return position;
    }

    @JSExport
    @JSProperty
    public int getEndPosition() {
        return endPosition;
    }

    @JSExport
    @JSProperty
    public String getMessage() {
        return message;
    }
}
