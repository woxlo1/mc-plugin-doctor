package io.github.woxlo1.plugindoctor.core.compatibility;

import java.util.List;
import java.util.Objects;

public record ClassFileInfo(
        String entryPath,
        String className,
        int minorVersion,
        int majorVersion,
        List<String> referencedClasses,
        List<ApiReference> apiReferences) {
    public ClassFileInfo {
        entryPath = requireText(entryPath, "entryPath");
        className = requireText(className, "className");
        if (minorVersion < 0 || minorVersion > 0xffff) {
            throw new IllegalArgumentException("minorVersion must be an unsigned 16-bit value");
        }
        if (majorVersion < 45 || majorVersion > 0xffff) {
            throw new IllegalArgumentException("majorVersion must be a supported class-file value");
        }
        referencedClasses = List.copyOf(
                Objects.requireNonNull(referencedClasses, "referencedClasses"));
        apiReferences = List.copyOf(Objects.requireNonNull(apiReferences, "apiReferences"));
        if (referencedClasses.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("referencedClasses must not contain blank values");
        }
    }

    public boolean usesPreviewFeatures() {
        return minorVersion == 0xffff;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
