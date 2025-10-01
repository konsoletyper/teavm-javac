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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

class FileData implements JavaFileObject {
    private final Map<String, ?> containingMap;
    static Map<String, Kind> kindByExtension = new HashMap<>();
    String path;
    byte[] data = new byte[0];
    long lastModified;
    boolean isJavaBase;

    static {
        for (var kind : Kind.values()) {
            if (!kind.extension.isEmpty()) {
                kindByExtension.put(kind.extension, kind);
            }
        }
    }

    FileData(Map<String, ?> containingMap) {
        this.containingMap = containingMap;
    }

    @Override
    public Kind getKind() {
        var index = path.lastIndexOf('.');
        if (index < 0) {
            return Kind.OTHER;
        }
        var result = kindByExtension.get(path.substring(index));
        return result == null ? Kind.OTHER : result;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return path.equals(simpleName.replace('.', '/') + kind.extension);
    }

    @Override
    public NestingKind getNestingKind() {
        return null;
    }

    @Override
    public Modifier getAccessLevel() {
        return null;
    }

    @Override
    public URI toUri() {
        try {
            return new URI("teavm:", "/" + path, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return path;
    }

    @Override
    public InputStream openInputStream() {
        return new ByteArrayInputStream(data);
    }

    @Override
    public OutputStream openOutputStream() {
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                data = toByteArray();
                lastModified = System.currentTimeMillis();
            }
        };
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors)  {
        return new InputStreamReader(openInputStream(), StandardCharsets.UTF_8);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public Writer openWriter() {
        return new OutputStreamWriter(openOutputStream(), StandardCharsets.UTF_8);
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public boolean delete() {
        return containingMap.remove(path, this);
    }
}
