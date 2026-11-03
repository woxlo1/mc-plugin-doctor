package io.github.woxlo1.plugindoctor.core.compatibility;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ClassFileReferenceReader {
    private static final int CLASS_FILE_MAGIC = 0xcafebabe;

    public ClassFileInfo read(InputStream source, String entryPath) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(entryPath, "entryPath");
        try (DataInputStream input = new DataInputStream(source)) {
            if (input.readInt() != CLASS_FILE_MAGIC) {
                throw new IOException("Invalid class-file magic");
            }
            int minorVersion = input.readUnsignedShort();
            int majorVersion = input.readUnsignedShort();
            if (majorVersion < 45) {
                throw new IOException("Unsupported class-file major version: " + majorVersion);
            }

            Object[] constants = readConstantPool(input);
            input.readUnsignedShort();
            int thisClassIndex = input.readUnsignedShort();
            input.readUnsignedShort();
            String className = className(constants, thisClassIndex);

            int interfaceCount = input.readUnsignedShort();
            for (int index = 0; index < interfaceCount; index++) {
                input.readUnsignedShort();
            }
            Set<String> referencedClasses = new LinkedHashSet<>();
            skipMembers(input, constants, referencedClasses);
            skipMembers(input, constants, referencedClasses);
            skipAttributes(input, constants);
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing bytes after the class file");
            }

            Set<ApiReference> references = new LinkedHashSet<>();
            for (Object constant : constants) {
                if (constant instanceof ClassConstant classConstant) {
                    for (String reference : classNames(utf8(constants, classConstant.nameIndex()))) {
                        referencedClasses.add(reference);
                    }
                } else if (constant instanceof MemberConstant memberConstant) {
                    String owner = className(constants, memberConstant.classIndex());
                    NameAndTypeConstant nameAndType = nameAndType(
                            constants, memberConstant.nameAndTypeIndex());
                    String name = utf8(constants, nameAndType.nameIndex());
                    String descriptor = utf8(constants, nameAndType.descriptorIndex());
                    addDescriptorClasses(descriptor, referencedClasses);
                    ApiReferenceKind kind = memberConstant.tag() == 9
                            ? ApiReferenceKind.FIELD : ApiReferenceKind.METHOD;
                    references.add(ApiReference.memberReference(kind, owner, name, descriptor));
                }
            }
            referencedClasses.forEach(reference ->
                    references.add(ApiReference.classReference(reference)));

            List<String> orderedClasses = referencedClasses.stream().sorted().toList();
            List<ApiReference> orderedReferences = references.stream()
                    .sorted(Comparator.comparing((ApiReference reference) -> reference.kind().ordinal())
                            .thenComparing(ApiReference::owner)
                            .thenComparing(reference -> reference.name().orElse(""))
                            .thenComparing(reference -> reference.descriptor().orElse("")))
                    .toList();
            return new ClassFileInfo(
                    entryPath,
                    className,
                    minorVersion,
                    majorVersion,
                    orderedClasses,
                    orderedReferences);
        }
    }

    private static Object[] readConstantPool(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        if (count == 0) {
            throw new IOException("Invalid zero-length constant pool");
        }
        Object[] constants = new Object[count];
        for (int index = 1; index < count; index++) {
            int tag = input.readUnsignedByte();
            switch (tag) {
                case 1 -> constants[index] = input.readUTF();
                case 3, 4 -> input.readInt();
                case 5, 6 -> {
                    if (index + 1 >= count) {
                        throw new IOException("Long or double constant has no second pool slot");
                    }
                    input.readLong();
                    index++;
                }
                case 7 -> constants[index] = new ClassConstant(input.readUnsignedShort());
                case 8, 16, 19, 20 -> input.readUnsignedShort();
                case 9, 10, 11 -> constants[index] =
                        new MemberConstant(tag, input.readUnsignedShort(), input.readUnsignedShort());
                case 12 -> constants[index] =
                        new NameAndTypeConstant(input.readUnsignedShort(), input.readUnsignedShort());
                case 15 -> {
                    input.readUnsignedByte();
                    input.readUnsignedShort();
                }
                case 17, 18 -> {
                    input.readUnsignedShort();
                    input.readUnsignedShort();
                }
                default -> throw new IOException("Unsupported constant-pool tag: " + tag);
            }
        }
        return constants;
    }

    private static void skipMembers(
            DataInputStream input, Object[] constants, Set<String> referencedClasses)
            throws IOException {
        int memberCount = input.readUnsignedShort();
        for (int index = 0; index < memberCount; index++) {
            input.readUnsignedShort();
            utf8(constants, input.readUnsignedShort());
            addDescriptorClasses(
                    utf8(constants, input.readUnsignedShort()), referencedClasses);
            skipAttributes(input, constants);
        }
    }

    private static void skipAttributes(DataInputStream input, Object[] constants) throws IOException {
        int attributeCount = input.readUnsignedShort();
        for (int index = 0; index < attributeCount; index++) {
            utf8(constants, input.readUnsignedShort());
            long length = Integer.toUnsignedLong(input.readInt());
            input.skipNBytes(length);
        }
    }

    private static String className(Object[] constants, int classIndex) throws IOException {
        if (classIndex <= 0
                || classIndex >= constants.length
                || !(constants[classIndex] instanceof ClassConstant classConstant)) {
            throw new IOException("Invalid class constant-pool index: " + classIndex);
        }
        List<String> names = classNames(utf8(constants, classConstant.nameIndex()));
        if (names.size() != 1) {
            throw new IOException("Invalid class name constant");
        }
        return names.get(0);
    }

    private static NameAndTypeConstant nameAndType(Object[] constants, int index)
            throws IOException {
        if (index <= 0
                || index >= constants.length
                || !(constants[index] instanceof NameAndTypeConstant nameAndType)) {
            throw new IOException("Invalid name-and-type constant-pool index: " + index);
        }
        return nameAndType;
    }

    private static String utf8(Object[] constants, int index) throws IOException {
        if (index <= 0 || index >= constants.length || !(constants[index] instanceof String value)) {
            throw new IOException("Invalid UTF-8 constant-pool index: " + index);
        }
        return value;
    }

    private static List<String> classNames(String value) throws IOException {
        if (!value.startsWith("[")) {
            return List.of(value.replace('/', '.'));
        }
        List<String> names = new ArrayList<>();
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != 'L') {
                continue;
            }
            int end = value.indexOf(';', index + 1);
            if (end < 0) {
                throw new IOException("Malformed array class descriptor");
            }
            names.add(value.substring(index + 1, end).replace('/', '.'));
            index = end;
        }
        return names;
    }

    private static void addDescriptorClasses(String descriptor, Set<String> referencedClasses)
            throws IOException {
        for (int index = 0; index < descriptor.length(); index++) {
            if (descriptor.charAt(index) != 'L') {
                continue;
            }
            int end = descriptor.indexOf(';', index + 1);
            if (end < 0) {
                throw new IOException("Malformed member descriptor");
            }
            referencedClasses.add(
                    descriptor.substring(index + 1, end).replace('/', '.'));
            index = end;
        }
    }

    private record ClassConstant(int nameIndex) {}

    private record MemberConstant(int tag, int classIndex, int nameAndTypeIndex) {}

    private record NameAndTypeConstant(int nameIndex, int descriptorIndex) {}
}
