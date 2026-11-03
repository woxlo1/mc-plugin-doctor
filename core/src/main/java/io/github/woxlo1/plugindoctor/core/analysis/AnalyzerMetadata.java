package io.github.woxlo1.plugindoctor.core.analysis;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

public record AnalyzerMetadata(
        String id,
        String name,
        String description,
        int priority,
        Set<String> dependencies,
        Duration timeout) {
    public AnalyzerMetadata {
        id = requireText(id, "id");
        name = requireText(name, "name");
        description = requireText(description, "description");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        if (dependencies.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("dependency IDs must not be blank");
        }
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("analyzer cannot depend on itself");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
