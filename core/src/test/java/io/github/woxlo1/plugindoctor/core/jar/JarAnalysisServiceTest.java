package io.github.woxlo1.plugindoctor.core.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.analysis.AnalysisException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarAnalysisServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsBasicReportWithManifestDescriptorsAndInventories() throws IOException {
        Path jar = createJar(Map.of(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nMain-Class: example.Main\n\n"
                        + "Name: example/Main.class\nSHA-256-Digest: fixture\n\n",
                "plugin.yml", "name: Example\nversion: 1\nmain: example.Main\n",
                "example/Main.class", "class bytes",
                "assets/config.yml", "enabled: true\n",
                "META-INF/NOTICE.txt", "notice"));

        JarAnalysisReport report = new JarAnalysisService().inspect(jar);

        assertEquals(jar.toAbsolutePath().normalize(), report.source());
        assertEquals(Files.size(jar), report.fileSize());
        assertEquals(5, report.entries().size());
        assertEquals("1.0", report.manifest().orElseThrow()
                .mainAttributes().get("Manifest-Version"));
        assertEquals("example.Main", report.manifest().orElseThrow()
                .mainAttributes().get("Main-Class"));
        assertTrue(report.manifest().orElseThrow().sections().containsKey("example/Main.class"));
        assertEquals(1, report.descriptors().size());
        assertEquals("example.Main", report.descriptors().get(0).mainClass().orElseThrow());
        assertEquals(java.util.List.of("example/Main.class"), report.classEntries());
        assertEquals(4, report.resourceEntries().size());
        assertTrue(report.findings().isEmpty());
    }

    @Test
    void preservesInventoryAndReportsMalformedManifestOrDescriptor() throws IOException {
        Path jar = createJar(Map.of(
                "META-INF/MANIFEST.MF", " continuation without an attribute\n",
                "plugin.yml", "name: ["));

        JarAnalysisReport report = new JarAnalysisService().inspect(jar);

        assertEquals(2, report.entries().size());
        assertTrue(report.manifest().isEmpty());
        assertTrue(report.findings().stream()
                .anyMatch(finding -> finding.ruleId().equals("jar.invalid-manifest")));
        assertTrue(report.findings().stream()
                .anyMatch(finding -> finding.ruleId().equals("descriptor.invalid")));
    }

    @Test
    void returnsAnExplicitErrorForUnreadableInput() {
        Path missing = temporaryDirectory.resolve("missing.jar");

        AnalysisException exception =
                assertThrows(AnalysisException.class, () -> new JarAnalysisService().inspect(missing));

        assertEquals("JAR_READ_FAILED", exception.code());
        assertTrue(exception.getMessage().contains(missing.toAbsolutePath().toString()));
    }

    @Test
    void reportsAreImmutableAndClassAndResourceInventoriesAreSorted() throws IOException {
        Path jar = createJar(Map.of(
                "z/Z.class", "class-z",
                "a/A.class", "class-a",
                "z-resource.txt", "resource-z",
                "a-resource.txt", "resource-a"));

        JarAnalysisReport report = new JarAnalysisService().inspect(jar);

        assertEquals(java.util.List.of("a/A.class", "z/Z.class"), report.classEntries());
        assertEquals(
                java.util.List.of("a-resource.txt", "z-resource.txt"),
                report.resourceEntries());
        assertThrows(UnsupportedOperationException.class, () -> report.classEntries().add("x.class"));
        assertFalse(report.entries().isEmpty());
    }

    private Path createJar(Map<String, String> contents) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "report-", ".jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(contents).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return jar;
    }
}
