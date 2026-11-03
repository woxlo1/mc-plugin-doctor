package io.github.woxlo1.plugindoctor.core.descriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.jar.JarArchive;
import io.github.woxlo1.plugindoctor.core.jar.JarReader;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginDescriptorAnalyzerTest {
    @TempDir
    Path temporaryDirectory;

    private final PluginDescriptorAnalyzer analyzer = new PluginDescriptorAnalyzer();

    @Test
    void parsesCommonPluginMetadataAndValidatesMainClassInventory() throws IOException {
        JarArchive archive = archive(Map.of(
                "plugin.yml",
                """
                name: Example
                version: 1.2.3
                main: example.Main
                api-version: '1.21'
                authors: [Alice, Bob]
                depend: [Core]
                softdepend: Optional
                loadbefore: [Other]
                libraries: [org.example:library:1.0]
                description: Example plugin
                website: https://example.test
                folia-supported: true
                commands:
                  example:
                    description: Example command
                permissions:
                  example.use:
                    default: true
                """,
                "example/Main.class", "class bytes"));

        DescriptorAnalysis result = analyzer.analyze(archive);

        assertEquals(1, result.descriptors().size());
        assertTrue(result.findings().isEmpty(), result.findings().toString());
        PluginDescriptor descriptor = result.descriptors().get(0);
        assertEquals(DescriptorType.BUKKIT, descriptor.type());
        assertEquals("Example", descriptor.name().orElseThrow());
        assertEquals("1.2.3", descriptor.version().orElseThrow());
        assertEquals("example.Main", descriptor.mainClass().orElseThrow());
        assertEquals("1.21", descriptor.apiVersion().orElseThrow());
        assertEquals(List.of("Alice", "Bob"), descriptor.authors());
        assertEquals(List.of("Core"), descriptor.dependencies());
        assertEquals(List.of("Optional"), descriptor.softDependencies());
        assertEquals(List.of("Other"), descriptor.loadBefore());
        assertEquals(List.of("org.example:library:1.0"), descriptor.libraries());
        assertEquals(Set.of("commands", "permissions"), descriptor.declaredSections());
        assertEquals(true, descriptor.foliaSupported().orElseThrow());
    }

    @Test
    void reportsMissingRequiredFieldsAndInvalidMainClassNames() throws IOException {
        DescriptorAnalysis missing = analyzer.analyze(archive(Map.of(
                "plugin.yml", "name: Example\n")));
        assertEquals(2, findingsWithRule(missing, "descriptor.missing-required-field").size());

        DescriptorAnalysis invalidMain = analyzer.analyze(archive(Map.of(
                "plugin.yml", "name: Example\nversion: 1\nmain: example..Main\n")));
        assertTrue(hasRule(invalidMain, "descriptor.invalid-main-class"));
        assertFalse(hasRule(invalidMain, "descriptor.main-class-not-found"));
    }

    @Test
    void reportsAWellFormedButMissingMainClass() throws IOException {
        DescriptorAnalysis result = analyzer.analyze(archive(Map.of(
                "plugin.yml", "name: Example\nversion: 1\nmain: example.Main\n")));

        assertTrue(hasRule(result, "descriptor.main-class-not-found"));
    }

    @Test
    void handlesMultiReleaseMainClassEntries() throws IOException {
        DescriptorAnalysis result = analyzer.analyze(archive(Map.of(
                "plugin.yml", "name: Example\nversion: 1\nmain: example.Main\n",
                "META-INF/versions/17/example/Main.class", "class bytes")));

        assertFalse(hasRule(result, "descriptor.main-class-not-found"));
    }

    @Test
    void reportsMalformedYamlDuplicateKeysAndNonMappingRoots() throws IOException {
        for (String yaml : List.of(
                "name: [",
                "name: First\nname: Second\n",
                "- not-a-mapping\n")) {
            DescriptorAnalysis result = analyzer.analyze(archive(Map.of("plugin.yml", yaml)));
            assertTrue(hasRule(result, "descriptor.invalid"));
            assertTrue(result.descriptors().isEmpty());
        }
    }

    @Test
    void reportsInvalidFieldTypesWithoutAbortingDescriptorAnalysis() throws IOException {
        DescriptorAnalysis result = analyzer.analyze(archive(Map.of(
                "plugin.yml",
                "name: Example\nversion: 1\nmain: 42\ndepend: {name: invalid}\n")));

        assertTrue(hasRule(result, "descriptor.invalid-field"));
        assertEquals(1, result.descriptors().size());
        assertTrue(result.descriptors().get(0).mainClass().isEmpty());
    }

    @Test
    void parsesPaperBungeeAndVelocityDescriptorFormats() throws IOException {
        JarArchive archive = archive(Map.of(
                "paper-plugin.yml", "name: PaperExample\nversion: 1\nmain: paper.Main\n",
                "bungee.yml", "name: BungeeExample\nversion: 1\nmain: bungee.Main\n",
                "velocity-plugin.json",
                "{\"id\":\"example\",\"name\":\"VelocityExample\",\"version\":\"1\","
                        + "\"main\":\"velocity.Main\",\"authors\":[\"Alice\"]}",
                "paper/Main.class", "class bytes",
                "bungee/Main.class", "class bytes",
                "velocity/Main.class", "class bytes"));

        DescriptorAnalysis result = analyzer.analyze(archive);

        assertEquals(3, result.descriptors().size());
        assertEquals(
                Set.of(DescriptorType.PAPER, DescriptorType.BUNGEE, DescriptorType.VELOCITY),
                result.descriptors().stream().map(PluginDescriptor::type).collect(
                        java.util.stream.Collectors.toSet()));
        assertTrue(result.findings().isEmpty(), result.findings().toString());
        assertEquals("example", result.descriptors().stream()
                .filter(descriptor -> descriptor.type() == DescriptorType.VELOCITY)
                .findFirst()
                .orElseThrow()
                .id()
                .orElseThrow());
    }

    @Test
    void reportsDuplicateJsonKeysAndTrailingContent() throws IOException {
        for (String json : List.of(
                "{\"id\":\"a\",\"id\":\"b\"}",
                "{\"name\":\"Example\"} trailing")) {
            DescriptorAnalysis result = analyzer.analyze(
                    archive(Map.of("velocity-plugin.json", json)));
            assertTrue(hasRule(result, "descriptor.invalid"));
        }
    }

    private JarArchive archive(Map<String, String> contents) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "descriptor-", ".jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(contents).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return new JarReader().read(jar);
    }

    private static boolean hasRule(DescriptorAnalysis result, String ruleId) {
        return result.findings().stream().anyMatch(finding -> finding.ruleId().equals(ruleId));
    }

    private static List<Finding> findingsWithRule(DescriptorAnalysis result, String ruleId) {
        return result.findings().stream()
                .filter(finding -> finding.ruleId().equals(ruleId))
                .toList();
    }
}
