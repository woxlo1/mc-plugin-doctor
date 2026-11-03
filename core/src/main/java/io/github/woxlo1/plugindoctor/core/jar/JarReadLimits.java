package io.github.woxlo1.plugindoctor.core.jar;

public record JarReadLimits(
        int maximumEntries,
        long maximumEntryBytes,
        long maximumTotalUncompressedBytes,
        double maximumCompressionRatio,
        long maximumDescriptorBytes) {

    public JarReadLimits {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        if (maximumEntryBytes < 1) {
            throw new IllegalArgumentException("maximumEntryBytes must be positive");
        }
        if (maximumTotalUncompressedBytes < 1) {
            throw new IllegalArgumentException("maximumTotalUncompressedBytes must be positive");
        }
        if (!Double.isFinite(maximumCompressionRatio) || maximumCompressionRatio < 1.0) {
            throw new IllegalArgumentException("maximumCompressionRatio must be finite and at least 1");
        }
        if (maximumDescriptorBytes < 1 || maximumDescriptorBytes > maximumEntryBytes) {
            throw new IllegalArgumentException(
                    "maximumDescriptorBytes must be positive and no greater than maximumEntryBytes");
        }
    }

    public static JarReadLimits defaults() {
        return new JarReadLimits(
                20_000,
                64L * 1024 * 1024,
                512L * 1024 * 1024,
                1_000.0,
                1024L * 1024);
    }
}
