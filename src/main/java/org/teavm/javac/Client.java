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

import com.sun.tools.javac.api.JavacTool;
import java.io.*;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.HTMLLinkElement;
import org.teavm.jso.typedarrays.Uint8Array;

public final class Client {
    private Client() {
    }

    public static void main(String[] args) throws IOException {
        createStdlib();

        HTMLDocument document = Window.current().getDocument();
        HTMLInputElement textArea = (HTMLInputElement) document.getElementById("source-code");
        document.getElementById("compile-button").addEventListener("click", event -> {
            try {
                createSourceFile(textArea.getValue());
                if (doCompile()) {
                    fetchResult();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static void fetchResult() throws IOException {
        File file = new File("/Hello.class");
        Uint8Array data = Uint8Array.create((int) file.length());
        try (FileInputStream input = new FileInputStream(file)) {
            int index = 0;
            while (true) {
                short b = (short) input.read();
                if (b < 0) {
                    break;
                }
                data.set(index++, b);
            }
        }

        HTMLDocument document = Window.current().getDocument();
        HTMLLinkElement element = (HTMLLinkElement) document.createElement("a");
        element.setHref(createObjectURL(data));
        setDownload(element, "Hello.class");
        document.getBody().appendChild(element);
        element.click();
        element.delete();
    }


    @JSBody(params = "data",
            script = "return URL.createObjectURL(new Blob([data], { type: 'application/octet-stream' }));")
    private static native String createObjectURL(Uint8Array data);

    @JSBody(params = { "target", "name" }, script = "target.download = name;")
    private static native void setDownload(HTMLLinkElement target, String name);

    private static boolean doCompile() throws IOException {
        JavaCompiler compiler = JavacTool.create();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                Arrays.asList(new File("/Hello.java")));
        OutputStreamWriter out = new OutputStreamWriter(System.out);

        JavaCompiler.CompilationTask task = compiler.getTask(out, fileManager, null, Arrays.asList("-verbose"),
                null, compilationUnits);
        boolean result = task.call();
        out.flush();

        return result;
    }

    private static void createStdlib() throws IOException {
        System.setProperty("sun.boot.class.path", "/stdlib");

        File baseDir = new File("/stdlib");
        baseDir.mkdirs();
        ClassLoader loader = ClassLoader.getSystemClassLoader();

        try (ZipInputStream input = new ZipInputStream(loader.getResourceAsStream("stdlib/data.zip"))) {
            while (true) {
                ZipEntry entry = input.getNextEntry();
                if (entry == null) {
                    break;
                }

                File outputFile = new File(baseDir, entry.getName());
                outputFile.getParentFile().mkdirs();
                try (OutputStream output = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[65536];
                    while (true) {
                        int bytesRead = input.read(buffer);
                        if (bytesRead < 0) {
                            break;
                        }
                        output.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
    }

    private static void createSourceFile(String content) throws IOException {
        File file = new File("/Hello.java");

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.write(content);
        }
    }
}
