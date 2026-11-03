package io.github.woxlo1.plugindoctor.core.analysis;

import java.util.Objects;

public final class AnalysisException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String componentId;

    public AnalysisException(String code, String componentId, String message) {
        super(requireText(message, "message"));
        this.code = requireText(code, "code");
        this.componentId = requireText(componentId, "componentId");
    }

    public AnalysisException(String code, String componentId, String message, Throwable cause) {
        super(requireText(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = requireText(code, "code");
        this.componentId = requireText(componentId, "componentId");
    }

    public String code() {
        return code;
    }

    public String componentId() {
        return componentId;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
