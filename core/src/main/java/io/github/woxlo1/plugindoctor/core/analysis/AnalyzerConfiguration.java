package io.github.woxlo1.plugindoctor.core.analysis;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AnalyzerConfiguration(
        boolean enabled,
        Map<String, String> options,
        Optional<Duration> timeoutOverride) {
    public AnalyzerConfiguration {
        options = Map.copyOf(Objects.requireNonNull(options, "options"));
        if (options.entrySet().stream()
                .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue() == null)) {
            throw new IllegalArgumentException("option keys must not be blank and values must not be null");
        }
        timeoutOverride = Objects.requireNonNull(timeoutOverride, "timeoutOverride");
        timeoutOverride.ifPresent(AnalyzerConfiguration::validateTimeout);
    }

    public static AnalyzerConfiguration defaults() {
        return new AnalyzerConfiguration(true, Map.of(), Optional.empty());
    }

    private static void validateTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
