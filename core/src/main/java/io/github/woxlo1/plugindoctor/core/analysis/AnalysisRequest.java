package io.github.woxlo1.plugindoctor.core.analysis;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record AnalysisRequest(Path target, Map<String, String> attributes) {
    public AnalysisRequest {
        target = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        if (attributes.entrySet().stream()
                .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue() == null)) {
            throw new IllegalArgumentException("attribute keys must not be blank and values must not be null");
        }
    }

    public static AnalysisRequest forTarget(Path target) {
        return new AnalysisRequest(target, Map.of());
    }
}
