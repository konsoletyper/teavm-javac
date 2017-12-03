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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class StdlibConverter extends ClassVisitor {
    private static final String PREFIX = "org/teavm/classlib/java/";
    private static final String[] EXCLUDED = {
            "org/teavm/classlib/",
            "org/teavm/jso/impl/",
            "org/teavm/platform",
            "java/nio/charset/impl/",
            "java/util/stream/impl",
            "java/util/stream/intimpl",
            "java/util/stream/longimpl",
            "java/util/stream/doubleimpl"
    };
    boolean visible;
    String className;

    public StdlibConverter(ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return;
        }
        name = rename(name);
        for (String excluded : EXCLUDED) {
            if (name.startsWith(excluded)) {
                return;
            }
        }

        visible = true;
        className = name;
        if (superName != null) {
            superName = rename(superName);
        }

        switch (name) {
            case "java/lang/Throwable":
                superName = "java/lang/Object";
                break;
            case "java/lang/RuntimeException":
                superName = "java/lang/Exception";
                break;
        }

        if (interfaces != null) {
            for (int i = 0; i < interfaces.length; ++i) {
                interfaces[i] = rename(interfaces[i]);
            }
        }
        if (signature != null) {
            signature = renameClassSignature(signature);
        }

        if (superName.equals(name)) {
            superName = null;
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if ((access & Opcodes.ACC_PUBLIC) == 0 && ((access & Opcodes.ACC_PROTECTED) == 0)) {
            return null;
        }
        desc = renameDesc(desc);
        if (signature != null) {
            signature = renameFieldSignature(signature);
        }
        return new FieldVisitorImpl(super.visitField(access, name, desc, signature, value));
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if ((access & Opcodes.ACC_PUBLIC) == 0 && ((access & Opcodes.ACC_PROTECTED) == 0)) {
            return null;
        }
        desc = renameMethodDesc(desc);
        if (signature != null) {
            signature = renameMethodSignature(signature);
        }

        if (exceptions != null) {
            for (int i = 0; i < exceptions.length; ++i) {
                exceptions[i] = rename(exceptions[i]);
            }
        }
        return new MethodVisitorImpl(super.visitMethod(access, name, desc, signature, exceptions));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return super.visitAnnotation(desc, visible);
    }

    class FieldVisitorImpl extends FieldVisitor {
        FieldVisitorImpl(FieldVisitor fv) {
            super(Opcodes.ASM5, fv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (!visible) {
                return null;
            }
            desc = renameDesc(desc);
            return new AnnotationVisitorImpl(super.visitAnnotation(desc, visible));
        }
    }

    class MethodVisitorImpl extends MethodVisitor {
        MethodVisitorImpl(MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (!visible) {
                return null;
            }
            desc = renameDesc(desc);
            return new AnnotationVisitorImpl(super.visitAnnotation(desc, visible));
        }
    }

    class AnnotationVisitorImpl extends AnnotationVisitor {
        AnnotationVisitorImpl(AnnotationVisitor av) {
            super(Opcodes.ASM5, av);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            if (!visible) {
                return null;
            }
            desc = renameDesc(desc);
            return new AnnotationVisitorImpl(super.visitAnnotation(name, desc));
        }

        @Override
        public void visit(String name, Object value) {
            if (value instanceof Type) {
                value = Type.getType(renameDesc(((Type) value).getDescriptor()));
            }
            super.visit(name, value);
        }
    }

    private String rename(String className) {
        if (!className.startsWith(PREFIX)) {
            return className;
        }
        int slashIndex = className.lastIndexOf('/');
        if (className.charAt(slashIndex + 1) != 'T') {
            return className;
        }

        return "java/" + className.substring(PREFIX.length(), slashIndex) + "/" + className.substring(slashIndex + 2);
    }

    private String renameDesc(String desc) {
        DescRename rename = new DescRename(desc);
        rename.renameType();
        return rename.sb.toString();
    }

    private String renameMethodDesc(String desc) {
        DescRename rename = new DescRename(desc);
        assert desc.charAt(0) == '(';
        rename.sb.append('(');
        rename.index++;
        while (desc.charAt(rename.index) != ')') {
            rename.renameType();
        }
        rename.sb.append(')');
        rename.index++;
        rename.renameType();
        return rename.sb.toString();
    }

    class DescRename {
        String desc;
        StringBuilder sb;
        int index;

        DescRename(String desc) {
            this.desc = desc;
            sb = new StringBuilder(desc.length());
        }

        void renameType() {
            char c = desc.charAt(index);
            switch (c) {
                case 'Z':
                case 'B':
                case 'C':
                case 'S':
                case 'I':
                case 'J':
                case 'F':
                case 'D':
                case 'V':
                    sb.append(c);
                    index++;
                    break;
                case '[':
                    sb.append(c);
                    index++;
                    renameType();
                    break;
                case 'L':
                    sb.append(c);
                    int classStart = ++index;
                    index = desc.indexOf(';', index);
                    sb.append(rename(desc.substring(classStart, index)));
                    sb.append(';');
                    index++;
                    break;
            }
        }
    }

    private String renameClassSignature(String signature) {
        SignatureRename rename = new SignatureRename(signature);

        if (rename.current() == '<') {
            renameTypeParameters(rename);
        }

        while (rename.hasMore()) {
            rename.renameClassType();
        }
        return rename.sb.toString();
    }

    private String renameFieldSignature(String signature) {
        SignatureRename rename = new SignatureRename(signature);
        rename.renameFieldType();
        return rename.sb.toString();
    }

    private String renameMethodSignature(String signature) {
        SignatureRename rename = new SignatureRename(signature);

        if (rename.current() == '<') {
            renameTypeParameters(rename);
        }

        assert rename.current() == '(';
        rename.nextChar();
        while (rename.current() != ')') {
            rename.renameType();
        }
        rename.nextChar();
        rename.renameType();

        while (rename.hasMore() && rename.current() == '^') {
            rename.nextChar();
            rename.renameFieldType();
        }
        return rename.sb.toString();
    }

    private void renameTypeParameters(SignatureRename rename) {
        rename.nextChar();
        while (rename.current() != '>') {
            rename.skipIdentifier();
            while (rename.current() == ':') {
                rename.nextChar();
                if (rename.current() != ':' && rename.current() != '>') {
                    rename.renameFieldType();
                }
            }
        }
        rename.nextChar();
    }

    class SignatureRename {
        StringBuilder sb;
        String signature;
        int index;

        SignatureRename(String signature) {
            this.signature = signature;
            sb = new StringBuilder(signature.length());
        }

        char current() {
            return signature.charAt(index);
        }

        boolean hasMore() {
            return index < signature.length();
        }

        void nextChar() {
            sb.append(signature.charAt(index++));
        }

        void skipIdentifier() {
            loop: while (index < signature.length()) {
                switch (current()) {
                    case '.':
                    case ';':
                    case '[':
                    case '/':
                    case '<':
                    case '>':
                    case ':':
                        break loop;
                    default:
                        nextChar();
                        break;
                }
            }
        }

        void renameType() {
            switch (current()) {
                case 'Z':
                case 'B':
                case 'C':
                case 'S':
                case 'I':
                case 'J':
                case 'F':
                case 'D':
                case 'V':
                    nextChar();
                    break;
                default:
                    renameFieldType();
                    break;
            }
        }

        void renameFieldType() {
            switch (current()) {
                case 'L':
                    renameClassType();
                    break;
                case 'T':
                    nextChar();
                    skipIdentifier();
                    assert current() == ';';
                    nextChar();
                    break;
                case '[':
                    nextChar();
                    renameType();
                    break;
            }
        }

        void renameClassType() {
            assert current() == 'L';
            nextChar();

            StringBuilder name = new StringBuilder();
            while (current() != '<' && current() != '.' && current() != ';') {
                name.append(signature.charAt(index++));
            }
            sb.append(rename(name.toString()));

            if (current() == '<') {
                nextChar();
                renameTypeArguments();
            }

            while (current() == '.') {
                nextChar();
                renameSimpleClassType();
            }

            assert current() == ';';
            nextChar();
        }

        void renameSimpleClassType() {
            while (current() != '<' && current() != '.' && current() != ';') {
                nextChar();
            }

            if (current() == '<') {
                nextChar();
                renameTypeArguments();
            }
        }

        void renameTypeArguments() {
            while (current() != '>') {
                if (current() == '*') {
                    nextChar();
                    continue;
                }
                if (current() == '+' || current() == '-') {
                    nextChar();
                }
                renameFieldType();
            }
            nextChar();
        }
    }

    public static void main(String[] args) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new FileInputStream("teavm-classlib.zip"));
                ZipOutputStream output = new ZipOutputStream(new FileOutputStream("classlib.zip"))) {
            while (true) {
                ZipEntry entry = input.getNextEntry();
                if (entry == null) {
                    break;
                }
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }

                ClassReader reader = new ClassReader(input);
                ClassWriter writer = new ClassWriter(0);
                StdlibConverter converter = new StdlibConverter(writer);
                reader.accept(converter, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                if (converter.visible) {
                    ZipEntry outEntry = new ZipEntry(converter.className + ".class");
                    output.putNextEntry(outEntry);
                    output.write(writer.toByteArray());
                }
            }
        }
    }
}
