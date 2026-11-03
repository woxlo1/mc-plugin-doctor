package io.github.woxlo1.plugindoctor.core.rules;

import io.github.woxlo1.plugindoctor.core.logging.AnalysisLogger;
import io.github.woxlo1.plugindoctor.core.model.AnalysisError;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RuleEngine {
    private final List<AnalysisRule> rules;

    public RuleEngine(List<? extends AnalysisRule> rules) {
        Objects.requireNonNull(rules, "rules");
        Set<String> identifiers = new HashSet<>();
        List<AnalysisRule> copy = new ArrayList<>(rules.size());
        for (AnalysisRule rule : rules) {
            Objects.requireNonNull(rule, "rule");
            String id = Objects.requireNonNull(rule.id(), "rule ID");
            if (id.isBlank()) {
                throw new IllegalArgumentException("rule ID must not be blank");
            }
            if (!identifiers.add(id)) {
                throw new IllegalArgumentException("duplicate rule ID: " + id);
            }
            copy.add(rule);
        }
        this.rules = List.copyOf(copy);
    }

    public RuleEngineResult apply(List<Finding> input, AnalysisLogger logger) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(logger, "logger");
        List<Finding> findings = new ArrayList<>(input.size());
        List<AnalysisError> errors = new ArrayList<>();
        Map<String, Integer> appliedRuleCounts = new LinkedHashMap<>();

        for (Finding original : input) {
            Finding current = Objects.requireNonNull(original, "finding");
            boolean suppressed = false;
            for (AnalysisRule rule : rules) {
                try {
                    RuleDecision decision = Objects.requireNonNull(
                            rule.evaluate(current),
                            "rule decision");
                    if (!decision.changedFinding()) {
                        continue;
                    }
                    appliedRuleCounts.merge(rule.id(), 1, Integer::sum);
                    logger.debug("Applied rule " + rule.id() + ": " + decision.rationale());
                    if (decision.suppressed()) {
                        suppressed = true;
                        break;
                    }
                    current = current.withSeverity(decision.severityOverride().orElseThrow());
                } catch (RuntimeException exception) {
                    AnalysisError error = new AnalysisError(
                            "RULE_EVALUATION_FAILED",
                            rule.id(),
                            "Rule evaluation failed: " + safeMessage(exception),
                            java.util.Optional.of(exception));
                    errors.add(error);
                    logger.error(error.message(), exception);
                }
            }
            if (!suppressed) {
                findings.add(current);
            }
        }

        return new RuleEngineResult(findings, errors, appliedRuleCounts);
    }

    private static String safeMessage(Throwable cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }
}
