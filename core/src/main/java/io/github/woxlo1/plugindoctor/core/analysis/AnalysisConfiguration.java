package io.github.woxlo1.plugindoctor.core.analysis;

import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record AnalysisConfiguration(
        Duration defaultAnalyzerTimeout,
        Severity minimumSeverity,
        boolean continueAfterAnalyzerFailure,
        Map<String, AnalyzerConfiguration> analyzers) {
    public AnalysisConfiguration {
        Objects.requireNonNull(defaultAnalyzerTimeout, "defaultAnalyzerTimeout");
        if (defaultAnalyzerTimeout.isZero() || defaultAnalyzerTimeout.isNegative()) {
            throw new IllegalArgumentException("defaultAnalyzerTimeout must be positive");
        }
        Objects.requireNonNull(minimumSeverity, "minimumSeverity");
        analyzers = Map.copyOf(Objects.requireNonNull(analyzers, "analyzers"));
        if (analyzers.entrySet().stream()
                .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue() == null)) {
            throw new IllegalArgumentException("analyzer IDs must not be blank and configurations must not be null");
        }
    }

    public static AnalysisConfiguration defaults() {
        return new AnalysisConfiguration(
                Duration.ofSeconds(30), Severity.INFO, true, Map.of());
    }

    public AnalyzerConfiguration configurationFor(String analyzerId) {
        return analyzers.getOrDefault(analyzerId, AnalyzerConfiguration.defaults());
    }

    public Duration timeoutFor(String analyzerId) {
        return configurationFor(analyzerId)
                .timeoutOverride()
                .orElse(defaultAnalyzerTimeout);
    }
}
