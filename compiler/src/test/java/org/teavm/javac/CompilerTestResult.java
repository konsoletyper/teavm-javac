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
package org.teavm.javac;

import java.util.List;

public record CompilerTestResult(
        Status status,
        List<Diagnostic> diagnostics,
        String stdout,
        String stderr,
        String exception
) {
    public enum Status {
        OK,
        JAVAC_FAILED,
        TEAVM_FAILED,
        EXCEPTION
    }

    public record Diagnostic(String type, String severity, String fileName, int lineNumber, String message) {
        @Override
        public String toString() {
            return "[" + type + " " + severity + "] " + fileName + ":" + lineNumber + ": " + message;
        }
    }

    public String describe() {
        var sb = new StringBuilder("status: ").append(status);
        for (var diagnostic : diagnostics) {
            sb.append("\n").append(diagnostic);
        }
        if (exception != null) {
            sb.append("\nexception: ").append(exception);
        }
        if (!stdout.isEmpty()) {
            sb.append("\nstdout:\n").append(stdout);
        }
        if (!stderr.isEmpty()) {
            sb.append("\nstderr:\n").append(stderr);
        }
        return sb.toString();
    }
}
