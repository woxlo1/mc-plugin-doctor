package io.github.woxlo1.plugindoctor.core.jar;

import java.util.Objects;

public record JarArchiveEntry(
        String path,
        boolean directory,
        long compressedSize,
        long uncompressedSize,
        String sha256) {
    public JarArchiveEntry {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (compressedSize < 0 || uncompressedSize < 0) {
            throw new IllegalArgumentException("entry sizes must not be negative");
        }
        Objects.requireNonNull(sha256, "sha256");
        if (sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must not be blank");
        }
    }
}
