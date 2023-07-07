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

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

public interface MarkOptions extends JSObject {
    @JSProperty
    void setClassName(String className);

    @JSProperty
    void setInclusiveLeft(boolean inclusiveLeft);

    @JSProperty
    void setInclusiveRight(boolean inclusiveRight);

    @JSProperty
    void setTitle(String title);
}
