package io.github.woxlo1.plugindoctor.core.model;

import java.util.Objects;
import java.util.Optional;

public record AnalysisError(
        String code,
        String componentId,
        String message,
        Optional<Throwable> cause) {
    public AnalysisError {
        code = requireText(code, "code");
        componentId = requireText(componentId, "componentId");
        message = requireText(message, "message");
        cause = Objects.requireNonNull(cause, "cause");
    }

    public AnalysisError(String code, String componentId, String message) {
        this(code, componentId, message, Optional.empty());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
