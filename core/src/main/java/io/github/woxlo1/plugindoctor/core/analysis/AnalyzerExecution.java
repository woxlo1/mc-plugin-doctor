package io.github.woxlo1.plugindoctor.core.analysis;

import io.github.woxlo1.plugindoctor.core.model.AnalysisError;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record AnalyzerExecution(
        String analyzerId,
        AnalyzerExecutionStatus status,
        Duration duration,
        int findingCount,
        Optional<AnalysisError> error) {
    public AnalyzerExecution {
        Objects.requireNonNull(analyzerId, "analyzerId");
        if (analyzerId.isBlank()) {
            throw new IllegalArgumentException("analyzerId must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (findingCount < 0) {
            throw new IllegalArgumentException("findingCount must not be negative");
        }
        error = Objects.requireNonNull(error, "error");
        if ((status == AnalyzerExecutionStatus.FAILED
                        || status == AnalyzerExecutionStatus.TIMED_OUT)
                && error.isEmpty()) {
            throw new IllegalArgumentException("failed and timed out executions must include an error");
        }
        if (status == AnalyzerExecutionStatus.SUCCEEDED && error.isPresent()) {
            throw new IllegalArgumentException("successful executions must not include an error");
        }
    }
}
