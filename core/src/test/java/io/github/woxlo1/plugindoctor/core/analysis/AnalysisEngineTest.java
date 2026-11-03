package io.github.woxlo1.plugindoctor.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.logging.AnalysisLogger;
import io.github.woxlo1.plugindoctor.core.model.AnalysisError;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import io.github.woxlo1.plugindoctor.core.rules.AnalysisRule;
import io.github.woxlo1.plugindoctor.core.rules.RuleDecision;
import io.github.woxlo1.plugindoctor.core.rules.RuleEngine;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class AnalysisEngineTest {
    private static final Duration ANALYZER_TIMEOUT = Duration.ofSeconds(2);
    private static final AnalysisConfiguration DEFAULT_CONFIGURATION =
            AnalysisConfiguration.defaults();

    @Test
    void executesDependenciesBeforeHigherPriorityAnalyzers() {
        List<String> order = new ArrayList<>();
        PluginAnalyzer dependent = analyzer(
                metadata("dependent", 100, Set.of("dependency")),
                context -> {
                    order.add("dependent");
                    return result(finding("dependent"));
                });
        PluginAnalyzer dependency = analyzer(
                metadata("dependency", -100, Set.of()),
                context -> {
                    order.add("dependency");
                    return result(finding("dependency"));
                });

        AnalysisResult result = analyze(engine(dependent, dependency), DEFAULT_CONFIGURATION);

        assertEquals(List.of("dependency", "dependent"), order);
        assertEquals(2, result.findings().size());
        assertTrue(result.isSuccessful());
    }

    @Test
    void continuesIndependentAnalyzersAfterFailureAndSkipsDependents() {
        PluginAnalyzer failing = analyzer(
                metadata("failing", 2, Set.of()),
                context -> {
                    throw new IllegalStateException("fixture failure");
                });
        PluginAnalyzer dependent = analyzer(
                metadata("dependent", 1, Set.of("failing")),
                context -> result(finding("dependent")));
        PluginAnalyzer independent = analyzer(
                metadata("independent", 0, Set.of()),
                context -> result(finding("independent")));

        AnalysisResult result = analyze(
                engine(failing, dependent, independent), DEFAULT_CONFIGURATION);

        assertFalse(result.isSuccessful());
        assertEquals(1, result.findings().size());
        assertEquals("independent", result.findings().get(0).analyzerId());
        assertEquals(AnalyzerExecutionStatus.FAILED, execution(result, "failing").status());
        assertEquals(AnalyzerExecutionStatus.SKIPPED, execution(result, "dependent").status());
        assertEquals(AnalyzerExecutionStatus.SUCCEEDED, execution(result, "independent").status());
    }

    @Test
    void timesOutAnalyzerAndStillRunsSubsequentAnalyzers() {
        PluginAnalyzer slow = analyzer(
                metadata("slow", 1, Set.of()),
                context -> {
                    Thread.sleep(2_000);
                    return result(finding("slow"));
                });
        PluginAnalyzer fast = analyzer(
                metadata("fast", 0, Set.of()),
                context -> result(finding("fast")));
        AnalysisConfiguration configuration = configuration(
                java.util.Map.of("slow", new AnalyzerConfiguration(
                        true, java.util.Map.of(), Optional.of(Duration.ofMillis(300)))));

        AnalysisResult result = analyze(engine(slow, fast), configuration);

        assertEquals(AnalyzerExecutionStatus.TIMED_OUT, execution(result, "slow").status());
        assertEquals(AnalyzerExecutionStatus.SUCCEEDED, execution(result, "fast").status());
        assertEquals(List.of("fast"), result.findings().stream()
                .map(Finding::analyzerId)
                .toList());
    }

    @Test
    void reportsMissingDependenciesAndCyclesWithoutRunningAffectedAnalyzers() {
        PluginAnalyzer missing = analyzer(
                metadata("missing", 2, Set.of("not-registered")),
                context -> result(finding("missing")));
        PluginAnalyzer cycleA = analyzer(
                metadata("cycle-a", 1, Set.of("cycle-b")),
                context -> result(finding("cycle-a")));
        PluginAnalyzer cycleB = analyzer(
                metadata("cycle-b", 0, Set.of("cycle-a")),
                context -> result(finding("cycle-b")));
        PluginAnalyzer cycleDependent = analyzer(
                metadata("cycle-dependent", -1, Set.of("cycle-a")),
                context -> result(finding("cycle-dependent")));

        AnalysisResult result = analyze(
                engine(missing, cycleA, cycleB, cycleDependent), DEFAULT_CONFIGURATION);

        assertTrue(result.errors().stream()
                .anyMatch(error -> error.code().equals("ANALYZER_DEPENDENCY_MISSING")));
        assertTrue(result.errors().stream()
                .filter(error -> error.code().equals("ANALYZER_DEPENDENCY_CYCLE"))
                .count() >= 2);
        assertEquals(AnalyzerExecutionStatus.SKIPPED, execution(result, "missing").status());
        assertEquals(AnalyzerExecutionStatus.SKIPPED, execution(result, "cycle-a").status());
        assertEquals(AnalyzerExecutionStatus.SKIPPED, execution(result, "cycle-b").status());
        assertEquals(
                AnalyzerExecutionStatus.SKIPPED,
                execution(result, "cycle-dependent").status());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.componentId().equals("cycle-dependent")
                        && error.code().equals("ANALYZER_DEPENDENCY_UNAVAILABLE")));
        assertFalse(result.errors().stream()
                .anyMatch(error -> error.componentId().equals("cycle-dependent")
                        && error.code().equals("ANALYZER_DEPENDENCY_CYCLE")));
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void appliesSeverityThresholdAndSkipsDisabledAnalyzers() {
        PluginAnalyzer low = analyzer(
                metadata("low", 1, Set.of()),
                context -> result(finding("low", Severity.LOW)));
        PluginAnalyzer disabled = analyzer(
                metadata("disabled", 0, Set.of()),
                context -> result(finding("disabled")));
        AnalysisConfiguration configuration = new AnalysisConfiguration(
                Duration.ofSeconds(2),
                Severity.MEDIUM,
                true,
                java.util.Map.of("disabled", new AnalyzerConfiguration(
                        false, java.util.Map.of(), Optional.empty())));

        AnalysisResult result = analyze(engine(low, disabled), configuration);

        assertTrue(result.findings().isEmpty());
        assertEquals(1, execution(result, "low").findingCount());
        assertEquals(AnalyzerExecutionStatus.SKIPPED, execution(result, "disabled").status());
    }

    @Test
    void rejectsFindingsClaimingAnotherAnalyzerIdentity() {
        PluginAnalyzer invalid = analyzer(
                metadata("owner", 1, Set.of()),
                context -> result(finding("different-owner")));

        AnalysisResult result = analyze(engine(invalid), DEFAULT_CONFIGURATION);

        assertEquals(AnalyzerExecutionStatus.FAILED, execution(result, "owner").status());
        assertEquals("INVALID_ANALYZER_RESULT", result.errors().get(0).code());
    }

    @Test
    void appliesRulesBeforeTheMinimumSeverityFilter() {
        PluginAnalyzer low = analyzer(
                metadata("low", 1, Set.of()),
                context -> result(finding("low", Severity.LOW)));
        AnalysisRule promote = new AnalysisRule() {
            @Override
            public String id() {
                return "rule.promote";
            }

            @Override
            public RuleDecision evaluate(Finding finding) {
                return RuleDecision.overrideSeverity(Severity.HIGH, "Promoted for test");
            }
        };
        AnalysisEngine engine = new AnalysisEngine(
                new AnalyzerRegistry(List.of(low)),
                new RuleEngine(List.of(promote)),
                new TestLogger());
        AnalysisConfiguration configuration = new AnalysisConfiguration(
                Duration.ofSeconds(2), Severity.MEDIUM, true, java.util.Map.of());

        AnalysisResult result = analyze(engine, configuration);

        assertEquals(Severity.HIGH, result.findings().get(0).severity());
    }

    @Test
    void registryRejectsDuplicateAnalyzerIds() {
        PluginAnalyzer first = analyzer(metadata("duplicate", 0, Set.of()), context -> AnalyzerResult.empty());
        PluginAnalyzer second = analyzer(metadata("duplicate", 1, Set.of()), context -> AnalyzerResult.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyzerRegistry(List.of(first, second)));
    }

    @Test
    void runsAnalyzerLifecycleAroundEachAnalysis() {
        List<String> lifecycle = new ArrayList<>();
        PluginAnalyzer managed = new PluginAnalyzer() {
            @Override
            public AnalyzerMetadata metadata() {
                return AnalysisEngineTest.metadata("managed", 0, Set.of());
            }

            @Override
            public void initialize(AnalyzerContext context) {
                lifecycle.add("initialize");
            }

            @Override
            public AnalyzerResult analyze(AnalyzerContext context) {
                lifecycle.add("analyze");
                return result(finding("managed"));
            }

            @Override
            public void close() {
                lifecycle.add("close");
            }
        };

        AnalysisResult result = analyze(engine(managed), DEFAULT_CONFIGURATION);

        assertTrue(result.isSuccessful());
        assertEquals(List.of("initialize", "analyze", "close"), lifecycle);
    }

    private static AnalysisEngine engine(PluginAnalyzer... analyzers) {
        return new AnalysisEngine(new AnalyzerRegistry(List.of(analyzers)), new TestLogger());
    }

    private static AnalysisResult analyze(
            AnalysisEngine engine, AnalysisConfiguration configuration) {
        return engine.analyze(AnalysisRequest.forTarget(Path.of("fixture.jar")), configuration);
    }

    private static AnalyzerMetadata metadata(String id, int priority, Set<String> dependencies) {
        return new AnalyzerMetadata(
                id,
                id,
                "Test analyzer",
                priority,
                dependencies,
                ANALYZER_TIMEOUT);
    }

    private static PluginAnalyzer analyzer(
            AnalyzerMetadata metadata, AnalyzerFunction function) {
        return new PluginAnalyzer() {
            @Override
            public AnalyzerMetadata metadata() {
                return metadata;
            }

            @Override
            public AnalyzerResult analyze(AnalyzerContext context) throws Exception {
                return function.analyze(context);
            }
        };
    }

    private static AnalyzerResult result(Finding finding) {
        return new AnalyzerResult(List.of(finding));
    }

    private static Finding finding(String analyzerId) {
        return finding(analyzerId, Severity.MEDIUM);
    }

    private static Finding finding(String analyzerId, Severity severity) {
        return new Finding(
                "rule." + analyzerId,
                analyzerId,
                severity,
                "test",
                "Test finding",
                "Finding generated for an engine test.",
                List.of("Observed by test"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                1.0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AnalyzerExecution execution(AnalysisResult result, String analyzerId) {
        return result.executions().stream()
                .filter(execution -> execution.analyzerId().equals(analyzerId))
                .findFirst()
                .orElseThrow();
    }

    private static AnalysisConfiguration configuration(
            java.util.Map<String, AnalyzerConfiguration> analyzers) {
        return new AnalysisConfiguration(Duration.ofSeconds(2), Severity.INFO, true, analyzers);
    }

    @FunctionalInterface
    private interface AnalyzerFunction {
        AnalyzerResult analyze(AnalyzerContext context) throws Exception;
    }

    private static final class TestLogger implements AnalysisLogger {
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final List<AnalysisError> errors = new CopyOnWriteArrayList<>();

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
