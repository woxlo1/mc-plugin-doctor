package io.github.woxlo1.plugindoctor.core.analysis;

import io.github.woxlo1.plugindoctor.core.model.AnalysisError;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AnalysisResult(
        UUID analysisId,
        Instant startedAt,
        Duration duration,
        List<Finding> findings,
        List<AnalyzerExecution> executions,
        List<AnalysisError> errors) {
    public AnalysisResult {
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        executions = List.copyOf(Objects.requireNonNull(executions, "executions"));
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    }

    public boolean isSuccessful() {
        return errors.isEmpty();
    }
}
