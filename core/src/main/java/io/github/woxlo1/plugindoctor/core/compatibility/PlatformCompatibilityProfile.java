package io.github.woxlo1.plugindoctor.core.compatibility;

import io.github.woxlo1.plugindoctor.core.descriptor.DescriptorType;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PlatformCompatibilityProfile(
        String platform,
        String apiVersion,
        int javaRelease,
        Set<DescriptorType> supportedDescriptors,
        List<ApiCatalogEntry> apiCatalog) {
    public PlatformCompatibilityProfile {
        platform = requireText(platform, "platform");
        apiVersion = requireText(apiVersion, "apiVersion");
        if (javaRelease < 1) {
            throw new IllegalArgumentException("javaRelease must be positive");
        }
        supportedDescriptors = Set.copyOf(
                Objects.requireNonNull(supportedDescriptors, "supportedDescriptors"));
        if (supportedDescriptors.isEmpty()) {
            throw new IllegalArgumentException("supportedDescriptors must not be empty");
        }
        apiCatalog = List.copyOf(Objects.requireNonNull(apiCatalog, "apiCatalog"));
        Set<ApiReference> references = new HashSet<>();
        for (ApiCatalogEntry entry : apiCatalog) {
            if (!references.add(entry.reference())) {
                throw new IllegalArgumentException(
                        "API catalog contains a duplicate reference: " + entry.reference());
            }
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
