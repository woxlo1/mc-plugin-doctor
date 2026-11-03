package io.github.woxlo1.plugindoctor.core.bytecode;

import io.github.woxlo1.plugindoctor.core.analysis.AnalysisException;
import io.github.woxlo1.plugindoctor.core.compatibility.ClassFileScanResult;
import io.github.woxlo1.plugindoctor.core.compatibility.ClassFileScanner;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class BytecodeAnalysisService {
    private static final String ANALYZER_ID = "bytecode.analyzer";
    private final ClassFileScanner classFileScanner;
    private final BytecodeAnalysisLimits limits;

    public BytecodeAnalysisService() {
        this(new ClassFileScanner(), BytecodeAnalysisLimits.defaults());
    }

    public BytecodeAnalysisService(
            ClassFileScanner classFileScanner, BytecodeAnalysisLimits limits) {
        this.classFileScanner = Objects.requireNonNull(classFileScanner, "classFileScanner");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public BytecodeAnalysisReport analyze(JarAnalysisReport report) {
        Objects.requireNonNull(report, "report");
        List<BytecodeClassInfo> classes = new ArrayList<>();
        List<BytecodeMethodInfo> methods = new ArrayList<>();
        List<BytecodeFieldInfo> fields = new ArrayList<>();
        Map<String, BytecodeNode> nodes = new TreeMap<>();
        List<BytecodeReference> references = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        ClassFileScanResult classFileScan = classFileScanner.scan(report, (entryPath, contents) -> {
            try {
                ClassArtifacts parsed = readClass(contents);
                classes.addAll(parsed.classes);
                methods.addAll(parsed.methods);
                fields.addAll(parsed.fields);
                for (BytecodeNode node : parsed.nodes.values()) {
                    if (!nodes.containsKey(node.id())
                            && nodes.size() >= limits.maximumGraphNodes()) {
                        throw limitExceeded(
                                "BYTECODE_NODE_LIMIT_EXCEEDED",
                                "Bytecode reference graph exceeds "
                                        + limits.maximumGraphNodes() + " nodes.");
                    }
                    mergeNode(nodes, node);
                }
                for (BytecodeReference reference : parsed.references) {
                    if (references.size() >= limits.maximumGraphReferences()) {
                        throw limitExceeded(
                                "BYTECODE_REFERENCE_LIMIT_EXCEEDED",
                                "Bytecode reference graph exceeds "
                                        + limits.maximumGraphReferences() + " edges.");
                    }
                    references.add(reference);
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                findings.add(invalidBytecode(entryPath));
            }
        });
        findings.addAll(classFileScan.findings());

        classes.sort(Comparator.comparing(BytecodeClassInfo::name));
        methods.sort(Comparator.comparing(BytecodeMethodInfo::id));
        fields.sort(Comparator.comparing(BytecodeFieldInfo::id));
        List<BytecodeReference> orderedReferences = references.stream()
                .distinct()
                .sorted(Comparator.comparing(BytecodeReference::sourceId)
                        .thenComparing(BytecodeReference::targetId)
                        .thenComparing(reference -> reference.kind().ordinal()))
                .toList();
        return new BytecodeAnalysisReport(
                report.source(),
                classes,
                methods,
                fields,
                new ReferenceGraph(nodes.values().stream().toList(), orderedReferences),
                findings);
    }

    private ClassArtifacts readClass(byte[] contents) {
        ClassArtifacts artifacts = new ClassArtifacts(limits);
        new ClassReader(contents).accept(artifacts.visitor(), 0);
        return artifacts;
    }

    private static void mergeNode(Map<String, BytecodeNode> nodes, BytecodeNode candidate) {
        BytecodeNode existing = nodes.get(candidate.id());
        if (existing == null || candidate.internal()) {
            nodes.put(candidate.id(), candidate);
        }
    }

    private static Finding invalidBytecode(String path) {
        return new Finding(
                "bytecode.invalid-class-file",
                ANALYZER_ID,
                Severity.HIGH,
                "bytecode",
                "Invalid bytecode class",
                "ASM could not interpret the class-file structure or bytecode instructions.",
                List.of("Entry: " + path),
                Optional.empty(),
                Optional.empty(),
                Optional.of(path),
                Optional.empty(),
                Optional.empty(),
                1.0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AnalysisException limitExceeded(String code, String message) {
        return new AnalysisException(code, ANALYZER_ID, message);
    }

    private static String className(String internalName) {
        return internalName.replace('/', '.');
    }

    private static String classNodeId(String name) {
        return "C:" + name;
    }

    private static String methodNodeId(String owner, String name, String descriptor) {
        return "M:" + owner + "#" + name + descriptor;
    }

    private static String fieldNodeId(String owner, String name, String descriptor) {
        return "F:" + owner + "#" + name + ":" + descriptor;
    }

    private static final class ClassArtifacts {
        private final BytecodeAnalysisLimits limits;
        private final List<BytecodeClassInfo> classes = new ArrayList<>();
        private final List<BytecodeMethodInfo> methods = new ArrayList<>();
        private final List<BytecodeFieldInfo> fields = new ArrayList<>();
        private final Map<String, BytecodeNode> nodes = new LinkedHashMap<>();
        private final List<BytecodeReference> references = new ArrayList<>();

        private ClassArtifacts(BytecodeAnalysisLimits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
        }

        private void addNode(BytecodeNode node) {
            BytecodeNode existing = nodes.get(node.id());
            if (existing == null) {
                if (nodes.size() >= limits.maximumGraphNodes()) {
                    throw limitExceeded(
                            "BYTECODE_NODE_LIMIT_EXCEEDED",
                            "Bytecode reference graph exceeds "
                                    + limits.maximumGraphNodes() + " nodes.");
                }
                nodes.put(node.id(), node);
            } else if (node.internal()) {
                nodes.put(node.id(), node);
            }
        }

        private void addReference(String sourceId, BytecodeNode target, BytecodeReferenceKind kind) {
            addNode(target);
            if (references.size() >= limits.maximumGraphReferences()) {
                throw limitExceeded(
                        "BYTECODE_REFERENCE_LIMIT_EXCEEDED",
                        "Bytecode reference graph exceeds "
                                + limits.maximumGraphReferences() + " edges.");
            }
            references.add(new BytecodeReference(sourceId, target.id(), kind));
        }

        private void addClassReference(
                String sourceId, String nameOrDescriptor, BytecodeReferenceKind kind) {
            if (nameOrDescriptor.startsWith("[")) {
                addTypeReference(sourceId, Type.getType(nameOrDescriptor), kind);
                return;
            }
            String name = className(nameOrDescriptor);
            addReference(
                    sourceId,
                    new BytecodeNode(
                            classNodeId(name),
                            BytecodeNodeKind.CLASS,
                            name,
                            Optional.empty(),
                            Optional.empty(),
                            false),
                    kind);
        }

        private void addTypeReference(
                String sourceId, Type type, BytecodeReferenceKind kind) {
            Type elementType = type;
            while (elementType.getSort() == Type.ARRAY) {
                elementType = elementType.getElementType();
            }
            if (elementType.getSort() == Type.OBJECT) {
                addClassReference(sourceId, elementType.getInternalName(), kind);
            } else if (elementType.getSort() == Type.METHOD) {
                addMethodDescriptorReferences(sourceId, elementType.getDescriptor());
            }
        }

        private void addMethodDescriptorReferences(String sourceId, String descriptor) {
            for (Type argument : Type.getArgumentTypes(descriptor)) {
                addTypeReference(sourceId, argument, BytecodeReferenceKind.METHOD_PARAMETER);
            }
            Type result = Type.getReturnType(descriptor);
            if (result.getSort() != Type.VOID) {
                addTypeReference(sourceId, result, BytecodeReferenceKind.METHOD_RETURN);
            }
        }

        private void addMemberReference(
                String sourceId,
                String owner,
                String name,
                String descriptor,
                BytecodeNodeKind kind,
                BytecodeReferenceKind referenceKind) {
            String normalizedOwner = className(owner);
            String id = kind == BytecodeNodeKind.METHOD
                    ? methodNodeId(normalizedOwner, name, descriptor)
                    : fieldNodeId(normalizedOwner, name, descriptor);
            addReference(
                    sourceId,
                    new BytecodeNode(
                            id,
                            kind,
                            normalizedOwner,
                            Optional.of(name),
                            Optional.of(descriptor),
                            false),
                    referenceKind);
        }

        private void addConstantReference(String sourceId, Object value) {
            ArrayDeque<Object> pending = new ArrayDeque<>();
            pending.push(value);
            while (!pending.isEmpty()) {
                Object constant = pending.pop();
                if (constant instanceof Type type) {
                    if (type.getSort() == Type.METHOD) {
                        addMethodDescriptorReferences(sourceId, type.getDescriptor());
                    } else {
                        addTypeReference(sourceId, type, BytecodeReferenceKind.CLASS_LITERAL);
                    }
                } else if (constant instanceof Handle handle) {
                    addHandleReference(sourceId, handle, BytecodeReferenceKind.BOOTSTRAP);
                } else if (constant instanceof ConstantDynamic dynamic) {
                    addTypeReference(
                            sourceId, Type.getType(dynamic.getDescriptor()), BytecodeReferenceKind.TYPE_USE);
                    addHandleReference(
                            sourceId, dynamic.getBootstrapMethod(), BytecodeReferenceKind.BOOTSTRAP);
                    for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                        pending.push(dynamic.getBootstrapMethodArgument(index));
                    }
                }
            }
        }

        private void addHandleReference(
                String sourceId, Handle handle, BytecodeReferenceKind defaultKind) {
            int tag = handle.getTag();
            if (tag >= Opcodes.H_GETFIELD && tag <= Opcodes.H_PUTSTATIC) {
                BytecodeReferenceKind kind = tag == Opcodes.H_GETFIELD || tag == Opcodes.H_GETSTATIC
                        ? BytecodeReferenceKind.FIELD_READ : BytecodeReferenceKind.FIELD_WRITE;
                addMemberReference(
                        sourceId,
                        handle.getOwner(),
                        handle.getName(),
                        handle.getDesc(),
                        BytecodeNodeKind.FIELD,
                        kind);
            } else {
                addMemberReference(
                        sourceId,
                        handle.getOwner(),
                        handle.getName(),
                        handle.getDesc(),
                        BytecodeNodeKind.METHOD,
                        defaultKind);
            }
        }

        private ClassVisitor visitor() {
            return new ClassVisitor(Opcodes.ASM9) {
                private String owner;
                private String ownerId;

                @Override
                public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String superName,
                        String[] interfaces) {
                    owner = className(name);
                    ownerId = classNodeId(owner);
                    int majorVersion = version & 0xffff;
                    int minorVersion = version >>> 16;
                            List<String> interfaceNames = interfaces == null
                                    ? List.of()
                                    : java.util.Arrays.stream(interfaces)
                                            .map(BytecodeAnalysisService::className)
                                            .toList();
                    classes.add(new BytecodeClassInfo(
                            owner,
                            access,
                            minorVersion,
                            majorVersion,
                            Optional.ofNullable(superName).map(BytecodeAnalysisService::className),
                            interfaceNames,
                            Optional.ofNullable(signature),
                            Optional.empty()));
                    addNode(new BytecodeNode(
                            ownerId,
                            BytecodeNodeKind.CLASS,
                            owner,
                            Optional.empty(),
                            Optional.empty(),
                            true));
                    if (superName != null) {
                        addClassReference(ownerId, superName, BytecodeReferenceKind.EXTENDS);
                    }
                    for (String interfaceName : interfaceNames) {
                        addClassReference(ownerId, interfaceName, BytecodeReferenceKind.IMPLEMENTS);
                    }
                }

                @Override
                public void visitSource(String source, String debug) {
                    if (!classes.isEmpty()) {
                        BytecodeClassInfo current = classes.remove(classes.size() - 1);
                        classes.add(new BytecodeClassInfo(
                                current.name(),
                                current.access(),
                                current.minorVersion(),
                                current.majorVersion(),
                                current.superName(),
                                current.interfaces(),
                                current.signature(),
                                Optional.ofNullable(source)));
                    }
                }

                @Override
                public FieldVisitor visitField(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        Object value) {
                    String id = fieldNodeId(owner, name, descriptor);
                    fields.add(new BytecodeFieldInfo(
                            owner,
                            name,
                            descriptor,
                            access,
                            Optional.ofNullable(signature),
                            Optional.ofNullable(value).map(Object::toString)));
                    addNode(new BytecodeNode(
                            id,
                            BytecodeNodeKind.FIELD,
                            owner,
                            Optional.of(name),
                            Optional.of(descriptor),
                            true));
                    addTypeReference(id, Type.getType(descriptor), BytecodeReferenceKind.FIELD_TYPE);
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                    String id = methodNodeId(owner, name, descriptor);
                    List<String> exceptionNames = exceptions == null
                            ? List.of()
                            : java.util.Arrays.stream(exceptions)
                                    .map(BytecodeAnalysisService::className)
                                    .toList();
                    methods.add(new BytecodeMethodInfo(
                            owner,
                            name,
                            descriptor,
                            access,
                            Optional.ofNullable(signature),
                            exceptionNames));
                    addNode(new BytecodeNode(
                            id,
                            BytecodeNodeKind.METHOD,
                            owner,
                            Optional.of(name),
                            Optional.of(descriptor),
                            true));
                    addMethodDescriptorReferences(id, descriptor);
                    exceptionNames.forEach(exception ->
                            addClassReference(id, exception, BytecodeReferenceKind.EXCEPTION));

                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String targetOwner,
                                String targetName,
                                String targetDescriptor,
                                boolean isInterface) {
                            addMemberReference(
                                    id,
                                    targetOwner,
                                    targetName,
                                    targetDescriptor,
                                    BytecodeNodeKind.METHOD,
                                    BytecodeReferenceKind.METHOD_CALL);
                        }

                        @Override
                        public void visitFieldInsn(
                                int opcode, String targetOwner, String targetName, String targetDescriptor) {
                            BytecodeReferenceKind kind = opcode == Opcodes.GETFIELD
                                            || opcode == Opcodes.GETSTATIC
                                    ? BytecodeReferenceKind.FIELD_READ
                                    : BytecodeReferenceKind.FIELD_WRITE;
                            addMemberReference(
                                    id,
                                    targetOwner,
                                    targetName,
                                    targetDescriptor,
                                    BytecodeNodeKind.FIELD,
                                    kind);
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            addClassReference(id, type, BytecodeReferenceKind.TYPE_USE);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            addConstantReference(id, value);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(
                                String invokedName,
                                String invokedDescriptor,
                                Handle bootstrapMethodHandle,
                                Object... bootstrapMethodArguments) {
                            addMethodDescriptorReferences(id, invokedDescriptor);
                            addHandleReference(
                                    id, bootstrapMethodHandle, BytecodeReferenceKind.BOOTSTRAP);
                            for (Object argument : bootstrapMethodArguments) {
                                addConstantReference(id, argument);
                            }
                        }

                        @Override
                        public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                            addTypeReference(
                                    id, Type.getType(descriptor), BytecodeReferenceKind.TYPE_USE);
                        }

                        @Override
                        public void visitTryCatchBlock(
                                org.objectweb.asm.Label start,
                                org.objectweb.asm.Label end,
                                org.objectweb.asm.Label handler,
                                String type) {
                            if (type != null) {
                                addClassReference(id, type, BytecodeReferenceKind.EXCEPTION);
                            }
                        }

                        @Override
                        public void visitFrame(
                                int type,
                                int numLocal,
                                Object[] local,
                                int numStack,
                                Object[] stack) {
                            addFrameTypes(id, numLocal, local);
                            addFrameTypes(id, numStack, stack);
                        }

                        @Override
                        public void visitLocalVariable(
                                String variableName,
                                String variableDescriptor,
                                String variableSignature,
                                org.objectweb.asm.Label start,
                                org.objectweb.asm.Label end,
                                int index) {
                            addTypeReference(
                                    id,
                                    Type.getType(variableDescriptor),
                                    BytecodeReferenceKind.TYPE_USE);
                        }
                    };
                }

                private void addFrameTypes(String sourceId, int count, Object[] values) {
                    for (int index = 0; index < count; index++) {
                        Object value = values[index];
                        if (value instanceof String typeName) {
                            addClassReference(sourceId, typeName, BytecodeReferenceKind.TYPE_USE);
                        }
                    }
                }
            };
        }

    }
}
