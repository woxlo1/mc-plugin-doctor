package io.github.woxlo1.plugindoctor.core.jar;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class JarReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void inventoriesEntriesAndCapturesOnlyMetadataFiles() throws IOException {
        Path jar = createJar(Map.of(
                "plugin.yml", "name: Example\nversion: 1.0\nmain: example.Main\n",
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n",
                "example/Main.class", "class bytes",
                "assets/example.txt", "resource data"));

        JarArchive archive = new JarReader().read(jar);

        assertEquals(4, archive.entries().size());
        assertEquals("name: Example\nversion: 1.0\nmain: example.Main\n",
                archive.textEntries().get("plugin.yml"));
        assertTrue(archive.textEntries().containsKey("META-INF/MANIFEST.MF"));
        assertEquals(2, archive.textEntries().size());
        assertTrue(archive.duplicateEntryNames().isEmpty());
        assertEquals(64, archive.entries().get(0).sha256().length());
    }

    @Test
    void acceptsAnEntryExactlyAtItsSizeLimitAndRejectsOneByteOver() throws IOException {
        JarReadLimits limits = limits(1, 4, 4, 1_000.0, 4);
        Path boundaryJar = createJar(Map.of("data.bin", "1234"));
        Path oversizedJar = createJar(Map.of("data.bin", "12345"));

        assertEquals(4, new JarReader(limits).read(boundaryJar).entries().get(0).uncompressedSize());
        assertEquals(
                "JAR_ENTRY_TOO_LARGE",
                assertThrows(AnalysisException.class, () -> new JarReader(limits).read(oversizedJar))
                        .code());
    }

    @Test
    void rejectsEntryCountAndTotalUncompressedSizeOverTheConfiguredLimits() throws IOException {
        JarReadLimits entryCountLimit = limits(1, 10, 10, 1_000.0, 10);
        Path twoEntries = createJar(Map.of("one", "a", "two", "b"));
        assertEquals(
                "JAR_ENTRY_LIMIT_EXCEEDED",
                assertThrows(
                                AnalysisException.class,
                                () -> new JarReader(entryCountLimit).read(twoEntries))
                        .code());

        JarReadLimits totalSizeLimit = limits(2, 10, 3, 1_000.0, 10);
        Path overTotal = createJar(Map.of("one", "aa", "two", "bb"));
        assertEquals(
                "JAR_TOTAL_SIZE_LIMIT_EXCEEDED",
                assertThrows(
                                AnalysisException.class,
                                () -> new JarReader(totalSizeLimit).read(overTotal))
                        .code());
    }

    @Test
    void rejectsUnsafeArchivePathsWithoutExtractingEntries() throws IOException {
        for (String unsafePath : new String[] {"../outside.txt", "/absolute.txt", "C:/drive.txt", "safe\\escape"}) {
            Path jar = createJar(Map.of(unsafePath, "data"));

            AnalysisException exception =
                    assertThrows(AnalysisException.class, () -> new JarReader().read(jar));

            assertEquals("JAR_UNSAFE_ENTRY", exception.code());
            assertTrue(exception.getMessage().contains(unsafePath));
        }
        assertTrue(Files.notExists(temporaryDirectory.resolve("outside.txt")));
    }

    @Test
    void rejectsHighlyCompressedEntriesAndOversizedMetadata() throws IOException {
        JarReadLimits ratioLimit = limits(2, 100_000, 100_000, 1.01, 100_000);
        Path compressed = createJar(Map.of("data.bin", "A".repeat(10_000)));
        assertEquals(
                "JAR_COMPRESSION_RATIO_EXCEEDED",
                assertThrows(
                                AnalysisException.class,
                                () -> new JarReader(ratioLimit).read(compressed))
                        .code());

        JarReadLimits descriptorLimit = limits(2, 100, 100, 1_000.0, 10);
        Path oversizedDescriptor = createJar(Map.of("plugin.yml", "01234567890"));
        assertEquals(
                "JAR_DESCRIPTOR_TOO_LARGE",
                assertThrows(
                                AnalysisException.class,
                                () -> new JarReader(descriptorLimit).read(oversizedDescriptor))
                        .code());
    }

    @Test
    void reportsMalformedArchivesAsAnalysisErrors() throws IOException {
        Path malformed = temporaryDirectory.resolve("malformed.jar");
        Files.writeString(malformed, "not a zip archive", StandardCharsets.UTF_8);

        AnalysisException exception =
                assertThrows(AnalysisException.class, () -> new JarReader().read(malformed));

        assertEquals("JAR_READ_FAILED", exception.code());
        assertTrue(exception.getMessage().contains(malformed.toAbsolutePath().toString()));
    }

    @Test
    void rejectsInvalidReadLimits() {
        assertThrows(IllegalArgumentException.class, () -> limits(0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 0.5, 1));
        assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 2));
    }

    private Path createJar(Map<String, String> contents) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "fixture-", ".jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(contents).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return jar;
    }

    private static JarReadLimits limits(
            int entries,
            long entryBytes,
            long totalBytes,
            double compressionRatio,
            long descriptorBytes) {
        return new JarReadLimits(
                entries, entryBytes, totalBytes, compressionRatio, descriptorBytes);
    }
}
