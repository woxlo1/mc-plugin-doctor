package io.github.woxlo1.plugindoctor.core.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.analysis.AnalysisException;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileScannerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void scansValidatedJarEntriesIntoClassFileMetadata() throws IOException {
        Path jar = createJar("fixture/Fixture.class", fixtureBytes());
        JarAnalysisReport report = new JarAnalysisService().inspect(jar);

        ClassFileScanResult result = new ClassFileScanner().scan(report);

        assertEquals(1, result.classes().size());
        assertTrue(result.classes().get(0).className().endsWith("$Fixture"));
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void reportsMalformedClassFilesWithoutDiscardingTheJarInventory() throws IOException {
        Path jar = createJar("fixture/Broken.class", new byte[] {1, 2, 3, 4});
        JarAnalysisReport report = new JarAnalysisService().inspect(jar);

        ClassFileScanResult result = new ClassFileScanner().scan(report);

        assertTrue(report.entries().stream().anyMatch(entry -> entry.path().equals("fixture/Broken.class")));
        assertTrue(result.classes().isEmpty());
        assertEquals("compatibility.invalid-class-file", result.findings().get(0).ruleId());
    }

    @Test
    void rejectsClassEntryChangesAfterTheJarReportWasCreated() throws IOException {
        Path jar = createJar("fixture/Fixture.class", fixtureBytes());
        JarAnalysisReport report = new JarAnalysisService().inspect(jar);
        byte[] changed = fixtureBytes();
        changed[changed.length - 1] ^= 1;
        createJar(jar, "fixture/Fixture.class", changed);

        AnalysisException exception =
                assertThrows(AnalysisException.class, () -> new ClassFileScanner().scan(report));

        assertEquals("CLASS_ARCHIVE_CHANGED", exception.code());
    }

    private Path createJar(String entry, byte[] contents) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "class-scan-", ".jar");
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
        String path = "/io/github/woxlo1/plugindoctor/core/compatibility/"
                + "ClassFileScannerTest$Fixture.class";
        try (InputStream input = ClassFileScannerTest.class.getResourceAsStream(path)) {
            return input.readAllBytes();
        }
    }

    private static final class Fixture {
        private static Map<String, String> values() {
            return Map.of("fixture", "value");
        }
    }
}
