package io.github.woxlo1.plugindoctor.core.analysis;

import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.util.List;
import java.util.Objects;

public record AnalyzerResult(List<Finding> findings) {
    public AnalyzerResult {
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    public static AnalyzerResult empty() {
        return new AnalyzerResult(List.of());
    }
}
