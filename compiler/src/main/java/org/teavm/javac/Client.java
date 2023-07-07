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

import com.sun.tools.javac.file.BaseFileManager;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

public final class Client {
    private Client() {
    }

    public static void main(String[] args) throws IOException {
        var context = new Context();
        var fileManager = createFileManager();
        Log.instance(context);
        //var t = (BasicJavacTask) BasicJavacTask.instance(context);
        var comp = JavaCompiler.instance(context);
        var inputFile = fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, "Main.java",
                JavaFileObject.Kind.SOURCE);
        comp.compile(List.of(inputFile), Collections.emptyList(), null, List.nil());
    }

    private static JavaFileManager createFileManager() {
        return null;
    }
}
