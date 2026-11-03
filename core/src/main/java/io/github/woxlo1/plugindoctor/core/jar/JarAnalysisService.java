package io.github.woxlo1.plugindoctor.core.jar;

import io.github.woxlo1.plugindoctor.core.descriptor.DescriptorAnalysis;
import io.github.woxlo1.plugindoctor.core.descriptor.PluginDescriptorAnalyzer;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public final class JarAnalysisService {
    private static final String ANALYZER_ID = "jar.analysis";
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private final JarReader jarReader;
    private final PluginDescriptorAnalyzer descriptorAnalyzer;

    public JarAnalysisService() {
        this(new JarReader(), new PluginDescriptorAnalyzer());
    }

    public JarAnalysisService(JarReader jarReader, PluginDescriptorAnalyzer descriptorAnalyzer) {
        this.jarReader = Objects.requireNonNull(jarReader, "jarReader");
        this.descriptorAnalyzer = Objects.requireNonNull(descriptorAnalyzer, "descriptorAnalyzer");
    }

    public JarAnalysisReport inspect(Path source) {
        JarArchive archive = jarReader.read(source);
        DescriptorAnalysis descriptorAnalysis = descriptorAnalyzer.analyze(archive);
        List<Finding> findings = new ArrayList<>(descriptorAnalysis.findings());
        Optional<ManifestInfo> manifest = parseManifest(archive, findings);

        List<String> classEntries = archive.entries().stream()
                .filter(entry -> !entry.directory() && entry.path().endsWith(".class"))
                .map(JarArchiveEntry::path)
                .sorted()
                .toList();
        List<String> resourceEntries = archive.entries().stream()
                .filter(entry -> !entry.directory() && !entry.path().endsWith(".class"))
                .map(JarArchiveEntry::path)
                .sorted()
                .toList();

        return new JarAnalysisReport(
                archive.source(),
                archive.fileSize(),
                archive.entries(),
                manifest,
                descriptorAnalysis.descriptors(),
                classEntries,
                resourceEntries,
                findings);
    }

    private static Optional<ManifestInfo> parseManifest(
            JarArchive archive, List<Finding> findings) {
        Optional<String> contents = archive.textEntries().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(MANIFEST_PATH))
                .map(Map.Entry::getValue)
                .findFirst();
        if (contents.isEmpty()) {
            return Optional.empty();
        }
        try {
            validateManifestSyntax(contents.get());
            Manifest manifest = new Manifest(new ByteArrayInputStream(
                    contents.get().getBytes(StandardCharsets.UTF_8)));
            return Optional.of(new ManifestInfo(
                    attributes(manifest.getMainAttributes()),
                    manifest.getEntries().entrySet().stream()
                            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                    Map.Entry::getKey,
                                    entry -> attributes(entry.getValue())))));
        } catch (IOException | IllegalArgumentException exception) {
            findings.add(new Finding(
                    "jar.invalid-manifest",
                    ANALYZER_ID,
                    Severity.MEDIUM,
                    "jar",
                    "Invalid JAR manifest",
                    "The manifest could not be parsed.",
                    List.of("Entry: " + MANIFEST_PATH),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(MANIFEST_PATH),
                    Optional.empty(),
                    Optional.empty(),
                    1.0,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()));
            return Optional.empty();
        }
    }

    private static void validateManifestSyntax(String contents) {
        boolean previousAttribute = false;
        for (String line : contents.split("\\r?\\n", -1)) {
            if (line.isEmpty()) {
                previousAttribute = false;
            } else if (line.charAt(0) == ' ') {
                if (!previousAttribute) {
                    throw new IllegalArgumentException("Manifest continuation has no attribute");
                }
            } else {
                int separator = line.indexOf(": ");
                if (separator <= 0) {
                    throw new IllegalArgumentException("Manifest attribute is malformed");
                }
                new Attributes.Name(line.substring(0, separator));
                previousAttribute = true;
            }
        }
    }

    private static Map<String, String> attributes(Attributes attributes) {
        Map<String, String> values = new TreeMap<>(Comparator.naturalOrder());
        attributes.forEach((key, value) ->
                values.put(key.toString(), value.toString()));
        return Map.copyOf(values);
    }

}
