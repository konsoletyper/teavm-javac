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
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.teavm.parsing.resource.Resource;
import org.teavm.parsing.resource.ResourceProvider;

class MemoryResourceProvider implements ResourceProvider {
    private final List<Map<String, FileData>> files;

    MemoryResourceProvider(List<Map<String, FileData>> files) {
        this.files = files;
    }

    @Override
    public Iterator<Resource> getResources(String name) {
       return files.stream()
               .map(map -> getResources(map, name))
               .filter(Objects::nonNull)
               .iterator();
    }

    private Resource getResources(Map<String, FileData> map, String name) {
        var file = map.get(name);
        if (file == null) {
            return null;
        }
        return new Resource() {
            @Override
            public InputStream open() {
                return new ByteArrayInputStream(file.data);
            }

            @Override
            public Date getModificationDate() {
                return new Date(file.lastModified);
            }
        };
    }

    @Override
    public void close() {
    }
}
