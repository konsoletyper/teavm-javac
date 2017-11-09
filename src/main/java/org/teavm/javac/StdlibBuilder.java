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

import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.*;

public final class StdlibBuilder {
    private static final String RELEVANT_RESOURCE = "java/lang/Object.class";

    private StdlibBuilder() {
    }

    public static void main(String[] args) throws IOException {
        ClassLoader classLoader = StdlibBuilder.class.getClassLoader();
        URL url = classLoader.getResource(RELEVANT_RESOURCE);
        if (url == null) {
            System.err.println("Stdlib was not found in classpath");
            System.exit(1);
        }

        String path = url.getPath().substring("file:".length());
        File jarFile = new File(path.substring(0, path.length() - RELEVANT_RESOURCE.length() - 2));
        File outFile = new File(args[0] + "/data.zip");
        outFile.getParentFile().mkdirs();

        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(jarFile));
                ZipOutputStream zipOutput = new ZipOutputStream(new FileOutputStream(outFile))) {
            while (true) {
                ZipEntry entry = zipInput.getNextEntry();
                if (entry == null) {
                    break;
                }
                if (!entry.getName().endsWith(".class") || !entry.getName().startsWith("java/")) {
                    continue;
                }

                int index = entry.getName().lastIndexOf('/');
                String correspondingTeaVMClass = "org/teavm/classlib/" + entry.getName().substring(0, index) + "/T"
                        + entry.getName().substring(index + 1);
                if (classLoader.getResource(correspondingTeaVMClass) == null) {
                    continue;
                }

                ClassReader classReader = new ClassReader(zipInput);
                ClassWriter classWriter = new ClassWriter(0);
                ClassVisitorImpl visitor = new ClassVisitorImpl(classWriter);
                classReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

                if (!visitor.skip) {
                    ZipEntry outputEntry = new ZipEntry(entry.getName());
                    zipOutput.putNextEntry(outputEntry);
                    zipOutput.write(classWriter.toByteArray());
                }
                zipInput.closeEntry();
            }
        }
    }

    static class ClassVisitorImpl extends ClassVisitor {
        boolean skip;

        public ClassVisitorImpl(ClassVisitor cv) {
            super(Opcodes.ASM5, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            if (shouldSkip(access, name)) {
                skip = true;
                return;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ((access & Opcodes.ACC_PUBLIC) == 0) {
                return null;
            }
            return super.visitMethod(access, name, desc, signature, exceptions);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            if ((access & Opcodes.ACC_PUBLIC) == 0) {
                return null;
            }
            return super.visitField(access, name, desc, signature, value);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if ((access & Opcodes.ACC_PUBLIC) == 0) {
                return;
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }

        private boolean shouldSkip(int access, String name) {
            if (!name.startsWith("java/")) {
                return true;
            }

            return (access & Opcodes.ACC_PUBLIC) == 0;
        }
    }
}
