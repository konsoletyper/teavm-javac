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

package org.teavm.javac.protocol;

import org.teavm.jso.JSProperty;

public interface CompilerDiagnosticMessage extends WorkerMessage {
    @JSProperty
    String getKind();

    @JSProperty
    void setKind(String kind);

    @JSProperty
    CompilableObject getObject();

    @JSProperty
    void setObject(CompilableObject object);

    @JSProperty
    int getStartPosition();

    @JSProperty
    void setStartPosition(int position);

    @JSProperty
    int getEndPosition();

    @JSProperty
    void setEndPosition(int position);

    @JSProperty
    int getPosition();

    @JSProperty
    void setPosition(int position);

    @JSProperty
    int getLineNumber();

    @JSProperty
    void setLineNumber(int lineNumber);

    @JSProperty
    int getColumnNumber();

    @JSProperty
    void setColumnNumber(int columnNumber);

    @JSProperty
    String getCode();

    @JSProperty
    void setCode(String code);

    @JSProperty
    String getMessage();

    @JSProperty
    void setMessage(String message);
}
