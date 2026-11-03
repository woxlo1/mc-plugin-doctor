package io.github.woxlo1.plugindoctor.core.jar;

import java.util.Map;
import java.util.Objects;

public record ManifestInfo(
        Map<String, String> mainAttributes,
        Map<String, Map<String, String>> sections) {
    public ManifestInfo {
        mainAttributes = Map.copyOf(Objects.requireNonNull(mainAttributes, "mainAttributes"));
        sections = Objects.requireNonNull(sections, "sections").entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Map.copyOf(entry.getValue())));
    }
}
