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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

class FileManagerImpl implements JavaFileManager {
    private final Map<Location, Map<String, FileData>> locations = new HashMap<>();

    FileManagerImpl(Map<String, FileData> sourcePath, Map<String, FileData> classPath, Map<String, FileData> sdkPath,
            Map<String, FileData> outputFiles) {
        locations.put(StandardLocation.SOURCE_PATH, sourcePath);
        locations.put(StandardLocation.CLASS_PATH, classPath);
        locations.put(StandardLocation.CLASS_OUTPUT, outputFiles);
        locations.put(StandardLocation.PLATFORM_CLASS_PATH, sdkPath);
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return FileManagerImpl.class.getClassLoader();
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
            boolean recurse) {
        var map = locations.get(location);
        if (map == null) {
            return Collections.emptyList();
        }
        var stream = map.values().stream();
        if (!packageName.isEmpty()) {
            var pathPrefix = packageName.replace('.', '/');
            stream = stream.filter(file -> {
                if (!file.path.startsWith(pathPrefix)) {
                    return false;
                }
                return file.path.length() == pathPrefix.length() || file.path.charAt(pathPrefix.length()) == '/';
            });
        }
        if (!recurse) {
            stream = stream.filter(file -> file.path.indexOf('/', packageName.length() + 1) < 0);
        }

        return stream.collect(Collectors.toList());
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (!(file instanceof FileData fileImpl)) {
            return null;
        }
        var result = fileImpl.path;
        var lastDotIndex = result.lastIndexOf('.');
        return result.substring(0, lastDotIndex).replace('/', '.');
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return a == b;
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        return false;
    }

    @Override
    public boolean hasLocation(Location location) {
        return locations.containsKey(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) {
        return getFileForInput(location, "", className.replace('.', '/') + kind.extension);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
            FileObject sibling) {
        return getFileForOutput(location, "", className.replace('.', '/') + kind.extension, sibling);
    }

    @Override
    public FileData getFileForInput(Location location, String packageName, String relativeName) {
        var map = locations.get(location);
        if (map == null) {
            return null;
        }
        var sb = new StringBuilder();
        if (!packageName.isEmpty()) {
            sb.append(packageName.replace('.', '/')).append('/');
        }
        var path = sb.append(relativeName).toString();
        return map.get(path);
    }

    @Override
    public FileData getFileForOutput(Location location, String packageName, String relativeName,
            FileObject sibling) {
        var map = locations.get(location);
        if (map == null) {
            return null;
        }
        var sb = new StringBuilder();
        if (!packageName.isEmpty()) {
            sb.append(packageName.replace('.', '/')).append('/');
        }
        var path = sb.append(relativeName).toString();
        var result = map.get(path);
        if (result == null) {
            result = new FileData(map);
            result.path = path;
            result.data = new byte[0];
            result.lastModified = System.currentTimeMillis();
            map.put(path, result);
        }
        return result;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close()  {
    }

    @Override
    public int isSupportedOption(String option) {
        return -1;
    }

    @Override
    public Iterable<Set<Location>> listLocationsForModules(Location location) {
        return List.of(Set.of(StandardLocation.PLATFORM_CLASS_PATH));
    }

    @Override
    public Location getLocationForModule(Location location, String moduleName) {
        if (moduleName.equals("java.base")) {
            return StandardLocation.PLATFORM_CLASS_PATH;
        }
        return null;
    }

    @Override
    public Location getLocationForModule(Location location, JavaFileObject fo) {
        if (!(fo instanceof FileData fileImpl)) {
            return null;
        }
        if (fileImpl.isJavaBase) {
            return StandardLocation.PLATFORM_CLASS_PATH;
        }
        return null;
    }

    @Override
    public String inferModuleName(Location location) {
        return location == StandardLocation.PLATFORM_CLASS_PATH ? "java.base" : null;
    }
}
