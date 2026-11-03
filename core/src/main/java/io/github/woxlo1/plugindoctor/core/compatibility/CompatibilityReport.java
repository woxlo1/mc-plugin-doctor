package io.github.woxlo1.plugindoctor.core.compatibility;

import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CompatibilityReport(
        String platform,
        String apiVersion,
        int targetJavaRelease,
        Optional<Integer> highestClassFileJavaRelease,
        Optional<String> buildJdk,
        List<ClassFileInfo> classes,
        List<ApiReference> apiReferences,
        List<Finding> findings) {
    public CompatibilityReport {
        platform = requireText(platform, "platform");
        apiVersion = requireText(apiVersion, "apiVersion");
        if (targetJavaRelease < 1) {
            throw new IllegalArgumentException("targetJavaRelease must be positive");
        }
        highestClassFileJavaRelease =
                Objects.requireNonNull(highestClassFileJavaRelease, "highestClassFileJavaRelease");
        buildJdk = Objects.requireNonNull(buildJdk, "buildJdk");
        if (highestClassFileJavaRelease.isPresent()
                && highestClassFileJavaRelease.get() < 1) {
            throw new IllegalArgumentException(
                    "highestClassFileJavaRelease must be positive when present");
        }
        if (buildJdk.isPresent() && buildJdk.get().isBlank()) {
            throw new IllegalArgumentException("buildJdk must not be blank when present");
        }
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        apiReferences = List.copyOf(Objects.requireNonNull(apiReferences, "apiReferences"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
