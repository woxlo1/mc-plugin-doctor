package io.github.woxlo1.plugindoctor.core.dependency;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record DependencyNode(
        String artifactId,
        String name,
        Optional<String> version,
        Path source) {
    public DependencyNode {
        artifactId = requireText(artifactId, "artifactId");
        name = requireText(name, "name");
        version = Objects.requireNonNull(version, "version");
        source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
