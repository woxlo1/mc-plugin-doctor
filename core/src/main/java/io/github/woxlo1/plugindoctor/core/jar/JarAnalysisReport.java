package io.github.woxlo1.plugindoctor.core.jar;

import io.github.woxlo1.plugindoctor.core.descriptor.PluginDescriptor;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JarAnalysisReport(
        Path source,
        long fileSize,
        List<JarArchiveEntry> entries,
        Optional<ManifestInfo> manifest,
        List<PluginDescriptor> descriptors,
        List<String> classEntries,
        List<String> resourceEntries,
        List<Finding> findings) {
    public JarAnalysisReport {
        source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        manifest = Objects.requireNonNull(manifest, "manifest");
        descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
        classEntries = List.copyOf(Objects.requireNonNull(classEntries, "classEntries"));
        resourceEntries = List.copyOf(Objects.requireNonNull(resourceEntries, "resourceEntries"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
}
