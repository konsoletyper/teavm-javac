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

package org.teavm.javac.ui.codemirror;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.dom.html.HTMLElement;

public abstract class CodeMirror implements JSObject {
    private CodeMirror() {
    }

    @JSBody(params = { "element", "config" }, script = "return CodeMirror.fromTextArea(element, config);")
    public static native CodeMirror fromTextArea(HTMLElement element, CodeMirrorConfig config);

    public abstract String getValue();

    public abstract void setValue(String value);

    public abstract int lineCount();

    public abstract JSArrayReader<Mark> getAllMarks();

    public abstract Mark markText(TextLocation from, TextLocation to, MarkOptions options);

    public abstract void clearGutter(String id);

    public abstract void setGutterMarker(int line, String gutterId, HTMLElement value);
}
