package io.github.woxlo1.plugindoctor.core.jar;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record JarArchive(
        Path source,
        long fileSize,
        List<JarArchiveEntry> entries,
        List<String> duplicateEntryNames,
        Map<String, String> textEntries) {
    public JarArchive {
        source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        duplicateEntryNames = List.copyOf(
                Objects.requireNonNull(duplicateEntryNames, "duplicateEntryNames"));
        textEntries = Map.copyOf(Objects.requireNonNull(textEntries, "textEntries"));
    }
}
