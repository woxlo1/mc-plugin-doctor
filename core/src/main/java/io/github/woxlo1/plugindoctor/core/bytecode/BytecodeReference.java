package io.github.woxlo1.plugindoctor.core.bytecode;

import java.util.Objects;

public record BytecodeReference(
        String sourceId,
        String targetId,
        BytecodeReferenceKind kind) {
    public BytecodeReference {
        sourceId = requireText(sourceId, "sourceId");
        targetId = requireText(targetId, "targetId");
        Objects.requireNonNull(kind, "kind");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
