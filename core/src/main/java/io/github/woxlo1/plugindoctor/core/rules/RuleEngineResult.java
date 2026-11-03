package io.github.woxlo1.plugindoctor.core.rules;

import io.github.woxlo1.plugindoctor.core.model.AnalysisError;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RuleEngineResult(
        List<Finding> findings,
        List<AnalysisError> errors,
        Map<String, Integer> appliedRuleCounts) {
    public RuleEngineResult {
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        appliedRuleCounts = Map.copyOf(Objects.requireNonNull(appliedRuleCounts, "appliedRuleCounts"));
    }
}
