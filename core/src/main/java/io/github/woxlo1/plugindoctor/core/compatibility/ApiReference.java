package io.github.woxlo1.plugindoctor.core.compatibility;

import java.util.Objects;
import java.util.Optional;

public record ApiReference(
        ApiReferenceKind kind,
        String owner,
        Optional<String> name,
        Optional<String> descriptor) {
    public ApiReference {
        Objects.requireNonNull(kind, "kind");
        owner = requireText(owner, "owner");
        name = Objects.requireNonNull(name, "name");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (kind == ApiReferenceKind.CLASS) {
            if (name.isPresent() || descriptor.isPresent()) {
                throw new IllegalArgumentException("class references must not have member details");
            }
        } else if (name.isEmpty() || descriptor.isEmpty()) {
            throw new IllegalArgumentException("member references require a name and descriptor");
        } else if (name.get().isBlank() || descriptor.get().isBlank()) {
            throw new IllegalArgumentException("member names and descriptors must not be blank");
        }
    }

    public static ApiReference classReference(String owner) {
        return new ApiReference(
                ApiReferenceKind.CLASS, owner, Optional.empty(), Optional.empty());
    }

    public static ApiReference memberReference(
            ApiReferenceKind kind, String owner, String name, String descriptor) {
        if (kind == ApiReferenceKind.CLASS) {
            throw new IllegalArgumentException("member references cannot have CLASS kind");
        }
        return new ApiReference(
                kind, owner, Optional.of(name), Optional.of(descriptor));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
