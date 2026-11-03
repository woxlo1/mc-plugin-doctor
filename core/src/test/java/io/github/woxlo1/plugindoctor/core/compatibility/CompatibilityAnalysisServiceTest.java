package io.github.woxlo1.plugindoctor.core.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.descriptor.DescriptorType;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisService;
import io.github.woxlo1.plugindoctor.core.jar.JarReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompatibilityAnalysisServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsJavaAndPlatformApiVersionIncompatibilitiesAndManifestBuildJdk()
            throws IOException {
        JarAnalysisReport report = analyzeJar(
                fixtureBytes(),
                "api-version: \"1.21\"\n",
                "Manifest-Version: 1.0\nBuild-Jdk-Spec: 17\n\n");

        CompatibilityReport result =
                new CompatibilityAnalysisService().analyze(report, profile(8, "1.20", List.of()));

        assertEquals(Optional.of(17), result.highestClassFileJavaRelease());
        assertEquals(Optional.of("17"), result.buildJdk());
        assertTrue(hasFinding(result, "compatibility.java-version-too-new"));
        assertTrue(hasFinding(result, "compatibility.api-version-newer-than-target"));
    }

    @Test
    void reportsDeprecatedAndRemovedApiReferencesFromTheSuppliedCatalog() throws IOException {
        JarAnalysisReport report = analyzeJar(fixtureBytes(), "api-version: \"1.20\"\n", "");
        List<ApiCatalogEntry> catalog = List.of(
                new ApiCatalogEntry(
                        ApiReference.classReference(JarReader.class.getName()),
                        ApiStatus.REMOVED,
                        Optional.empty()),
                new ApiCatalogEntry(
                        ApiReference.memberReference(
                                ApiReferenceKind.METHOD,
                                JarReader.class.getName(),
                                "read",
                                "(Ljava/nio/file/Path;)Lio/github/woxlo1/plugindoctor/core/jar/JarArchive;"),
                        ApiStatus.DEPRECATED,
                        Optional.of("Use the replacement API")));

        CompatibilityReport result =
                new CompatibilityAnalysisService().analyze(report, profile(17, "1.20", catalog));

        assertTrue(hasFinding(result, "compatibility.removed-api"));
        assertTrue(hasFinding(result, "compatibility.deprecated-api"));
        assertTrue(result.apiReferences().contains(ApiReference.classReference(JarReader.class.getName())));
    }

    @Test
    void reportsUnsupportedDescriptorsAndMismatchedPreviewClassVersions() throws IOException {
        byte[] previewClass = fixtureBytes();
        previewClass[4] = (byte) 0xff;
        previewClass[5] = (byte) 0xff;
        JarAnalysisReport report = analyzeJar(previewClass, "api-version: \"1.20\"\n", "");
        PlatformCompatibilityProfile profile = new PlatformCompatibilityProfile(
                "Velocity",
                "3.4.0",
                8,
                Set.of(DescriptorType.VELOCITY),
                List.of());

        CompatibilityReport result =
                new CompatibilityAnalysisService().analyze(report, profile);

        assertTrue(hasFinding(result, "compatibility.unsupported-descriptor"));
        assertTrue(hasFinding(result, "compatibility.preview-java-mismatch"));
    }

    @Test
    void rejectsDuplicateEntriesInAnApiCatalog() {
        ApiReference reference = ApiReference.classReference("org.example.Api");
        ApiCatalogEntry entry = new ApiCatalogEntry(reference, ApiStatus.AVAILABLE, Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> profile(17, "1.20", List.of(entry, entry)));
    }

    @Test
    void selectsMultiReleaseClassesForTheTargetJavaRuntime() throws IOException {
        byte[] baseClass = fixtureBytes();
        baseClass[6] = 0;
        baseClass[7] = 52;
        byte[] java17Class = fixtureBytes();
        Path jar = Files.createTempFile(temporaryDirectory, "multi-release-", ".jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            put(output, "plugin.yml", """
                    name: Example
                    version: 1
                    main: io.github.woxlo1.plugindoctor.core.compatibility.ClassFileReferenceReaderTest$Fixture
                    api-version: "1.20"
                    """);
            put(output, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMulti-Release: true\n\n");
            putClass(output, "io/github/woxlo1/plugindoctor/core/compatibility/"
                    + "ClassFileReferenceReaderTest$Fixture.class", baseClass);
            putClass(output, "META-INF/versions/17/io/github/woxlo1/plugindoctor/core/compatibility/"
                    + "ClassFileReferenceReaderTest$Fixture.class", java17Class);
        }
        JarAnalysisReport report = new JarAnalysisService().inspect(jar);
        CompatibilityAnalysisService service = new CompatibilityAnalysisService();

        CompatibilityReport java8 = service.analyze(report, profile(8, "1.20", List.of()));
        CompatibilityReport java17 = service.analyze(report, profile(17, "1.20", List.of()));

        assertEquals(Optional.of(8), java8.highestClassFileJavaRelease());
        assertEquals(Optional.of(17), java17.highestClassFileJavaRelease());
        assertFalse(hasFinding(java8, "compatibility.java-version-too-new"));
    }

    private JarAnalysisReport analyzeJar(
            byte[] classBytes, String extraDescriptor, String manifest) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "compatibility-", ".jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            put(output, "plugin.yml", """
                    name: Example
                    version: 1
                    main: io.github.woxlo1.plugindoctor.core.compatibility.ClassFileReferenceReaderTest$Fixture
                    %s
                    """.formatted(extraDescriptor));
            if (!manifest.isEmpty()) {
                put(output, "META-INF/MANIFEST.MF", manifest);
            }
            putClass(
                    output,
                    "io/github/woxlo1/plugindoctor/core/compatibility/"
                            + "ClassFileReferenceReaderTest$Fixture.class",
                    classBytes);
        }
        return new JarAnalysisService().inspect(jar);
    }

    private static void put(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void putClass(ZipOutputStream output, String name, byte[] contents)
            throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents);
        output.closeEntry();
    }

    private static byte[] fixtureBytes() throws IOException {
        String path = "/io/github/woxlo1/plugindoctor/core/compatibility/"
                + "ClassFileReferenceReaderTest$Fixture.class";
        try (InputStream input = CompatibilityAnalysisServiceTest.class.getResourceAsStream(path)) {
            return input.readAllBytes();
        }
    }

    private static PlatformCompatibilityProfile profile(
            int javaRelease, String apiVersion, List<ApiCatalogEntry> catalog) {
        return new PlatformCompatibilityProfile(
                "Paper",
                apiVersion,
                javaRelease,
                Set.of(DescriptorType.BUKKIT, DescriptorType.PAPER),
                catalog);
    }

    private static boolean hasFinding(CompatibilityReport report, String ruleId) {
        return report.findings().stream().anyMatch(finding -> finding.ruleId().equals(ruleId));
    }
}
