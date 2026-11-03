package io.github.woxlo1.plugindoctor.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Finding(
        String ruleId,
        String analyzerId,
        Severity severity,
        String category,
        String title,
        String description,
        List<String> evidence,
        Optional<String> affectedClass,
        Optional<String> affectedMethod,
        Optional<String> affectedFile,
        Optional<SourceLocation> sourceLocation,
        Optional<String> suggestedFix,
        double confidence,
        Optional<String> platform,
        Optional<String> version,
        Optional<String> documentationReference) {

    public Finding {
        ruleId = requireText(ruleId, "ruleId");
        analyzerId = requireText(analyzerId, "analyzerId");
        severity = Objects.requireNonNull(severity, "severity");
        category = requireText(category, "category");
        title = requireText(title, "title");
        description = requireText(description, "description");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (evidence.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("evidence entries must not be blank");
        }
        affectedClass = clean(affectedClass, "affectedClass");
        affectedMethod = clean(affectedMethod, "affectedMethod");
        affectedFile = clean(affectedFile, "affectedFile");
        sourceLocation = Objects.requireNonNull(sourceLocation, "sourceLocation");
        suggestedFix = clean(suggestedFix, "suggestedFix");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        platform = clean(platform, "platform");
        version = clean(version, "version");
        documentationReference = clean(documentationReference, "documentationReference");
    }

    public Finding withSeverity(Severity replacement) {
        return new Finding(
                ruleId,
                analyzerId,
                replacement,
                category,
                title,
                description,
                evidence,
                affectedClass,
                affectedMethod,
                affectedFile,
                sourceLocation,
                suggestedFix,
                confidence,
                platform,
                version,
                documentationReference);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <T> Optional<T> clean(Optional<T> value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isPresent() && value.get() instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank when present");
        }
        return value;
    }
}
