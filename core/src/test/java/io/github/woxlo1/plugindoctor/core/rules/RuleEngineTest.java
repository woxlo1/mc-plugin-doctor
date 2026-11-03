package io.github.woxlo1.plugindoctor.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.logging.AnalysisLogger;
import io.github.woxlo1.plugindoctor.core.model.AnalysisError;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleEngineTest {
    @Test
    void appliesSeverityOverridesInOrderAndSuppressesMatchingFindings() {
        RuleEngine engine = new RuleEngine(List.of(
                rule("raise", finding -> RuleDecision.overrideSeverity(
                        Severity.HIGH, "Elevated by policy")),
                rule("suppress-high", finding -> finding.severity() == Severity.HIGH
                        ? RuleDecision.suppress("Suppressed by policy")
                        : RuleDecision.keep())));
        RecordingLogger logger = new RecordingLogger();

        RuleEngineResult result = engine.apply(List.of(finding("analyzer")), logger);

        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.appliedRuleCounts().get("raise"));
        assertEquals(1, result.appliedRuleCounts().get("suppress-high"));
        assertTrue(logger.messages.size() >= 2);
    }

    @Test
    void reportsRuleFailuresAndContinuesWithRemainingRules() {
        RuleEngine engine = new RuleEngine(List.of(
                rule("broken", finding -> {
                    throw new IllegalStateException("invalid rule input");
                }),
                rule("raise", finding -> RuleDecision.overrideSeverity(
                        Severity.CRITICAL, "Applied after failure"))));
        RecordingLogger logger = new RecordingLogger();

        RuleEngineResult result = engine.apply(List.of(finding("analyzer")), logger);

        assertEquals(1, result.errors().size());
        assertEquals("RULE_EVALUATION_FAILED", result.errors().get(0).code());
        assertEquals(Severity.CRITICAL, result.findings().get(0).severity());
        assertEquals(1, logger.errors.size());
    }

    @Test
    void rejectsDuplicateRuleIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuleEngine(List.of(
                        rule("same", finding -> RuleDecision.keep()),
                        rule("same", finding -> RuleDecision.keep()))));
    }

    private static AnalysisRule rule(String id, RuleFunction function) {
        return new AnalysisRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuleDecision evaluate(Finding finding) {
                return function.evaluate(finding);
            }
        };
    }

    private static Finding finding(String analyzerId) {
        return new Finding(
                "rule.example",
                analyzerId,
                Severity.LOW,
                "quality",
                "Example finding",
                "Example description.",
                List.of("Observed evidence"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0.8,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    @FunctionalInterface
    private interface RuleFunction {
        RuleDecision evaluate(Finding finding);
    }

    private static final class RecordingLogger implements AnalysisLogger {
        private final List<String> messages = new ArrayList<>();
        private final List<AnalysisError> errors = new ArrayList<>();

        @Override
        public void debug(String message) {
            messages.add(message);
        }

        @Override
        public void info(String message) {
            messages.add(message);
        }

        @Override
        public void warn(String message) {
            messages.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {
            errors.add(new AnalysisError("TEST_LOG", "test", message));
        }
    }
}
