package io.github.woxlo1.plugindoctor.core.compatibility;

import java.util.Objects;
import java.util.Optional;

public record ApiCatalogEntry(
        ApiReference reference,
        ApiStatus status,
        Optional<String> replacement) {
    public ApiCatalogEntry {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(status, "status");
        replacement = Objects.requireNonNull(replacement, "replacement");
        if (replacement.isPresent() && replacement.get().isBlank()) {
            throw new IllegalArgumentException("replacement must not be blank when present");
        }
    }
}
