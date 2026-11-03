package io.github.woxlo1.plugindoctor.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.analysis.AnalysisException;
import io.github.woxlo1.plugindoctor.core.compatibility.ClassFileScanner;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class BytecodeAnalysisServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exploresClassMembersAndBuildsBytecodeReferenceGraph() throws IOException {
        JarAnalysisReport report = analyzeJar(fixtureBytes());

        BytecodeAnalysisReport result = new BytecodeAnalysisService().analyze(report);

        String owner = Fixture.class.getName();
        String readMethod = "M:" + owner + "#readValue()Ljava/lang/String;";
        String writeMethod = "M:" + owner + "#writeValue(Ljava/lang/String;)V";
        String field = "F:" + owner + "#value:Ljava/lang/String;";
        assertEquals(1, result.classes().size());
        assertEquals(owner, result.classes().get(0).name());
        assertTrue(result.methods().stream().anyMatch(method -> method.name().equals("exercise")));
        assertTrue(result.fields().stream().anyMatch(info -> info.name().equals("value")));
        assertTrue(result.referenceGraph().edges().stream().anyMatch(edge ->
                edge.sourceId().equals("C:" + owner)
                        && edge.targetId().equals("C:java.lang.Object")
                        && edge.kind() == BytecodeReferenceKind.EXTENDS));
        assertTrue(result.referenceGraph().edges().stream().anyMatch(edge ->
                edge.sourceId().equals(field)
                        && edge.targetId().equals("C:java.lang.String")
                        && edge.kind() == BytecodeReferenceKind.FIELD_TYPE));
        assertTrue(result.referenceGraph().edges().stream().anyMatch(edge ->
                edge.sourceId().equals(readMethod)
                        && edge.targetId().equals(field)
                        && edge.kind() == BytecodeReferenceKind.FIELD_READ));
        assertTrue(result.referenceGraph().edges().stream().anyMatch(edge ->
                edge.sourceId().equals(writeMethod)
                        && edge.targetId().equals(field)
                        && edge.kind() == BytecodeReferenceKind.FIELD_WRITE));
        assertTrue(result.referenceGraph().edges().stream().anyMatch(edge ->
                edge.kind() == BytecodeReferenceKind.METHOD_CALL
                        && edge.targetId().contains("java.util.Objects#requireNonNull")));
        assertFalse(result.findings().stream()
                .anyMatch(finding -> finding.ruleId().equals("bytecode.invalid-class-file")));
        assertThrows(UnsupportedOperationException.class, () -> result.classes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.referenceGraph().edges().clear());
    }

    @Test
    void reportsMalformedClassFiles() throws IOException {
        Path jar = createJar("fixture/Broken.class", new byte[] {1, 2, 3, 4});
        JarAnalysisReport report = new JarAnalysisService().inspect(jar);

        BytecodeAnalysisReport result = new BytecodeAnalysisService().analyze(report);

        assertTrue(result.classes().isEmpty());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.ruleId().equals("compatibility.invalid-class-file")));
    }

    @Test
    void reportsBytecodeThatPassesClassFileInventoryButFailsAsmParsing() throws IOException {
        Path jar = createJar("fixture/BrokenBytecode.class", invalidOpcodeClassBytes());
        JarAnalysisReport report = new JarAnalysisService().inspect(jar);

        BytecodeAnalysisReport result = new BytecodeAnalysisService().analyze(report);

        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.ruleId().equals("bytecode.invalid-class-file")));
    }

    @Test
    void enforcesReferenceGraphNodeAndEdgeLimits() throws IOException {
        JarAnalysisReport report = analyzeJar(fixtureBytes());

        AnalysisException nodeLimit = assertThrows(
                AnalysisException.class,
                () -> new BytecodeAnalysisService(
                                new ClassFileScanner(), new BytecodeAnalysisLimits(1, 10_000))
                        .analyze(report));
        AnalysisException edgeLimit = assertThrows(
                AnalysisException.class,
                () -> new BytecodeAnalysisService(
                                new ClassFileScanner(), new BytecodeAnalysisLimits(10_000, 1))
                        .analyze(report));

        assertEquals("BYTECODE_NODE_LIMIT_EXCEEDED", nodeLimit.code());
        assertEquals("BYTECODE_REFERENCE_LIMIT_EXCEEDED", edgeLimit.code());
    }

    @Test
    void rejectsArchiveChangesAfterJarInspection() throws IOException {
        Path jar = createJar(classEntryPath(), fixtureBytes());
        JarAnalysisReport report = new JarAnalysisService().inspect(jar);
        createJar(jar, classEntryPath(), new byte[] {1, 2, 3, 4});

        AnalysisException exception = assertThrows(
                AnalysisException.class,
                () -> new BytecodeAnalysisService().analyze(report));

        assertEquals("CLASS_ARCHIVE_CHANGED", exception.code());
    }

    private JarAnalysisReport analyzeJar(byte[] classContents) throws IOException {
        return new JarAnalysisService().inspect(createJar(classEntryPath(), classContents));
    }

    private Path createJar(String entry, byte[] contents) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "bytecode-", ".jar");
        createJar(jar, entry, contents);
        return jar;
    }

    private static void createJar(Path jar, String entry, byte[] contents) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry(entry));
            output.write(contents);
            output.closeEntry();
        }
    }

    private static byte[] fixtureBytes() throws IOException {
        String resource = "/" + classEntryPath();
        try (InputStream input = BytecodeAnalysisServiceTest.class.getResourceAsStream(resource)) {
            return input.readAllBytes();
        }
    }

    private static String classEntryPath() {
        return BytecodeAnalysisServiceTest.class.getName().replace('.', '/') + "$Fixture.class";
    }

    private static byte[] invalidOpcodeClassBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/BrokenBytecode", null,
                "java/lang/Object", null);
        MethodVisitor method =
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "broken", "()V", null, null);
        method.visitCode();
        method.visitInsn(255);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class Fixture {
        private String value;

        private String readValue() {
            return value;
        }

        private void writeValue(String value) {
            this.value = value;
        }

        private String exercise() {
            writeValue(java.util.Objects.requireNonNull(readValue()));
            return readValue();
        }
    }
}
