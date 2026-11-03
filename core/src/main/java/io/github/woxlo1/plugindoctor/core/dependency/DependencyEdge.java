package io.github.woxlo1.plugindoctor.core.dependency;

import java.util.List;
import java.util.Objects;

public record DependencyEdge(
        String sourceArtifactId,
        String targetName,
        DependencyKind kind,
        List<String> targetArtifactIds) {
    public DependencyEdge {
        sourceArtifactId = requireText(sourceArtifactId, "sourceArtifactId");
        targetName = requireText(targetName, "targetName");
        Objects.requireNonNull(kind, "kind");
        targetArtifactIds = List.copyOf(
                Objects.requireNonNull(targetArtifactIds, "targetArtifactIds"));
        if (targetArtifactIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("targetArtifactIds must not contain blank values");
        }
    }

    public boolean resolved() {
        return !targetArtifactIds.isEmpty();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
