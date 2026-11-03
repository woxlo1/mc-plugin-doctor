package io.github.woxlo1.plugindoctor.core.model;

import java.util.Objects;

public record SourceLocation(String file, int line, int column) {
    public SourceLocation {
        file = requireText(file, "file");
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be positive");
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
