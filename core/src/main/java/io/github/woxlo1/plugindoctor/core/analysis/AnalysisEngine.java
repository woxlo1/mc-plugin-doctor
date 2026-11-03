package io.github.woxlo1.plugindoctor.core.analysis;

import io.github.woxlo1.plugindoctor.core.logging.AnalysisLogger;
import io.github.woxlo1.plugindoctor.core.model.AnalysisError;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AnalysisEngine {
    private static final String ENGINE_ID = "core.analysis-engine";

    private final AnalyzerRegistry registry;
    private final AnalysisLogger logger;

    public AnalysisEngine(AnalyzerRegistry registry, AnalysisLogger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public AnalysisResult analyze(AnalysisRequest request, AnalysisConfiguration configuration) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(configuration, "configuration");
        UUID analysisId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();
        logger.info("Starting analysis " + analysisId + " for " + request.target());

        List<Finding> findings = new ArrayList<>();
        List<AnalyzerExecution> executions = new ArrayList<>();
        List<AnalysisError> errors = new ArrayList<>();
        ExecutionPlan plan = createPlan();
        errors.addAll(plan.errors());

        Map<String, AnalyzerExecutionStatus> statuses = new HashMap<>();
        for (String analyzerId : plan.cyclicAnalyzerIds()) {
            AnalysisError error = new AnalysisError(
                    "ANALYZER_DEPENDENCY_CYCLE",
                    analyzerId,
                    "Analyzer dependency cycle detected.");
            errors.add(error);
            executions.add(skipped(analyzerId, error));
            statuses.put(analyzerId, AnalyzerExecutionStatus.SKIPPED);
        }

        for (AnalyzerRegistry.RegisteredAnalyzer registered : plan.orderedAnalyzers()) {
            AnalyzerMetadata metadata = registered.metadata();
            if (plan.analyzersWithMissingDependencies().contains(metadata.id())) {
                AnalysisError error = plan.errors().stream()
                        .filter(candidate -> candidate.componentId().equals(metadata.id()))
                        .filter(candidate -> candidate.code().equals("ANALYZER_DEPENDENCY_MISSING"))
                        .findFirst()
                        .orElseThrow();
                executions.add(skipped(metadata.id(), error));
                statuses.put(metadata.id(), AnalyzerExecutionStatus.SKIPPED);
                logger.warn(error.message());
                continue;
            }
            if (Thread.currentThread().isInterrupted()) {
                AnalysisError error = new AnalysisError(
                        "ANALYSIS_INTERRUPTED", ENGINE_ID, "Analysis was interrupted.");
                errors.add(error);
                executions.add(skipped(metadata.id(), error));
                statuses.put(metadata.id(), AnalyzerExecutionStatus.SKIPPED);
                continue;
            }

            Optional<String> unavailableDependency = metadata.dependencies().stream()
                    .filter(dependency -> statuses.get(dependency) != AnalyzerExecutionStatus.SUCCEEDED)
                    .sorted()
                    .findFirst();
            if (unavailableDependency.isPresent()) {
                AnalysisError error = new AnalysisError(
                        "ANALYZER_DEPENDENCY_UNAVAILABLE",
                        metadata.id(),
                        "Required analyzer did not complete successfully: "
                                + unavailableDependency.get());
                errors.add(error);
                executions.add(skipped(metadata.id(), error));
                statuses.put(metadata.id(), AnalyzerExecutionStatus.SKIPPED);
                logger.warn(error.message());
                continue;
            }

            AnalyzerConfiguration analyzerConfiguration =
                    configuration.configurationFor(metadata.id());
            if (!analyzerConfiguration.enabled()) {
                executions.add(new AnalyzerExecution(
                        metadata.id(),
                        AnalyzerExecutionStatus.SKIPPED,
                        Duration.ZERO,
                        0,
                        Optional.empty()));
                statuses.put(metadata.id(), AnalyzerExecutionStatus.SKIPPED);
                logger.info("Skipped disabled analyzer " + metadata.id());
                continue;
            }

            Duration timeout = analyzerConfiguration.timeoutOverride()
                    .orElse(minimum(metadata.timeout(), configuration.defaultAnalyzerTimeout()));
            ExecutionAttempt attempt = execute(registered, request, configuration, timeout);
            executions.add(attempt.execution());
            statuses.put(metadata.id(), attempt.execution().status());
            attempt.error().ifPresent(errors::add);
            findings.addAll(attempt.findings().stream()
                    .filter(finding -> finding.severity().isAtLeast(configuration.minimumSeverity()))
                    .toList());

            if (attempt.execution().status() == AnalyzerExecutionStatus.SUCCEEDED) {
                logger.info("Completed analyzer " + metadata.id());
            } else if (attempt.error().isPresent()) {
                AnalysisError error = attempt.error().get();
                logger.error(error.message(), error.cause().orElseGet(
                        () -> new AnalysisException(error.code(), error.componentId(), error.message())));
            }

            if (!configuration.continueAfterAnalyzerFailure()
                    && attempt.execution().status() != AnalyzerExecutionStatus.SUCCEEDED) {
                for (AnalyzerRegistry.RegisteredAnalyzer remaining : plan.orderedAnalyzers()) {
                    if (!statuses.containsKey(remaining.metadata().id())) {
                        AnalysisError error = new AnalysisError(
                                "ANALYSIS_STOPPED_AFTER_FAILURE",
                                remaining.metadata().id(),
                                "Analysis stopped after an analyzer failure.");
                        errors.add(error);
                        executions.add(skipped(remaining.metadata().id(), error));
                        statuses.put(remaining.metadata().id(), AnalyzerExecutionStatus.SKIPPED);
                    }
                }
                break;
            }
        }

        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        logger.info("Finished analysis " + analysisId + " with " + findings.size() + " findings");
        return new AnalysisResult(
                analysisId, startedAt, duration, findings, executions, errors);
    }

    private ExecutionAttempt execute(
            AnalyzerRegistry.RegisteredAnalyzer registered,
            AnalysisRequest request,
            AnalysisConfiguration configuration,
            Duration timeout) {
        String analyzerId = registered.metadata().id();
        long startNanos = System.nanoTime();
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "plugin-doctor-" + analyzerId);
            thread.setDaemon(true);
            return thread;
        });
        Future<AnalyzerResult> future = executor.submit(
                () -> registered.analyzer().analyze(
                        new AnalyzerContext(request, configuration, logger)));
        try {
            AnalyzerResult result = Objects.requireNonNull(
                    future.get(timeoutNanos(timeout), TimeUnit.NANOSECONDS),
                    "analyzer result");
            for (Finding finding : result.findings()) {
                if (!finding.analyzerId().equals(analyzerId)) {
                    throw new AnalysisException(
                            "INVALID_ANALYZER_RESULT",
                            analyzerId,
                            "Analyzer returned a finding owned by " + finding.analyzerId());
                }
            }
            return new ExecutionAttempt(
                    new AnalyzerExecution(
                            analyzerId,
                            AnalyzerExecutionStatus.SUCCEEDED,
                            elapsed(startNanos),
                            result.findings().size(),
                            Optional.empty()),
                    result.findings(),
                    Optional.empty());
        } catch (TimeoutException exception) {
            future.cancel(true);
            AnalysisError error = new AnalysisError(
                    "ANALYZER_TIMED_OUT",
                    analyzerId,
                    "Analyzer exceeded its timeout of " + timeout + ".",
                    Optional.of(exception));
            return failedAttempt(
                    analyzerId, AnalyzerExecutionStatus.TIMED_OUT, startNanos, error);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            AnalysisError error = new AnalysisError(
                    "ANALYSIS_INTERRUPTED",
                    analyzerId,
                    "Analysis was interrupted while waiting for the analyzer.",
                    Optional.of(exception));
            return failedAttempt(
                    analyzerId, AnalyzerExecutionStatus.FAILED, startNanos, error);
        } catch (ExecutionException | RuntimeException exception) {
            Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            if (cause instanceof Error fatalError) {
                throw fatalError;
            }
            AnalysisError error;
            if (cause instanceof AnalysisException analysisException) {
                error = new AnalysisError(
                        analysisException.code(),
                        analysisException.componentId(),
                        analysisException.getMessage(),
                        Optional.of(analysisException));
            } else {
                error = new AnalysisError(
                        "ANALYZER_FAILED",
                        analyzerId,
                        "Analyzer failed: " + safeMessage(cause),
                        Optional.of(cause));
            }
            return failedAttempt(
                    analyzerId, AnalyzerExecutionStatus.FAILED, startNanos, error);
        } finally {
            executor.shutdownNow();
        }
    }

    private ExecutionPlan createPlan() {
        List<AnalyzerRegistry.RegisteredAnalyzer> all = registry.findAll();
        Map<String, AnalyzerRegistry.RegisteredAnalyzer> byId = new LinkedHashMap<>();
        all.forEach(analyzer -> byId.put(analyzer.metadata().id(), analyzer));
        Comparator<AnalyzerRegistry.RegisteredAnalyzer> order = Comparator
                .comparingInt((AnalyzerRegistry.RegisteredAnalyzer analyzer) ->
                        analyzer.metadata().priority())
                .reversed()
                .thenComparing(analyzer -> analyzer.metadata().id());
        PriorityQueue<AnalyzerRegistry.RegisteredAnalyzer> ready =
                new PriorityQueue<>(order);
        Map<String, Integer> pendingDependencies = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        List<AnalysisError> errors = new ArrayList<>();

        for (AnalyzerRegistry.RegisteredAnalyzer analyzer : all) {
            String analyzerId = analyzer.metadata().id();
            int presentDependencies = 0;
            for (String dependency : analyzer.metadata().dependencies()) {
                if (!byId.containsKey(dependency)) {
                    errors.add(new AnalysisError(
                            "ANALYZER_DEPENDENCY_MISSING",
                            analyzerId,
                            "Required analyzer is not registered: " + dependency));
                } else {
                    presentDependencies++;
                    dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>())
                            .add(analyzerId);
                }
            }
            pendingDependencies.put(analyzerId, presentDependencies);
            if (presentDependencies == 0) {
                ready.add(analyzer);
            }
        }

        List<AnalyzerRegistry.RegisteredAnalyzer> ordered = new ArrayList<>();
        Set<String> analyzersWithMissingDependencies = new HashSet<>();
        while (!ready.isEmpty()) {
            AnalyzerRegistry.RegisteredAnalyzer analyzer = ready.remove();
            ordered.add(analyzer);
            for (String dependentId : dependents.getOrDefault(
                    analyzer.metadata().id(), List.of())) {
                int remaining = pendingDependencies.compute(
                        dependentId, (ignored, count) -> Objects.requireNonNull(count) - 1);
                if (remaining == 0) {
                    ready.add(byId.get(dependentId));
                }
                for (AnalysisError error : errors) {
                    if (error.code().equals("ANALYZER_DEPENDENCY_MISSING")) {
                        analyzersWithMissingDependencies.add(error.componentId());
                    }
                }
            }
        }

        Set<String> scheduledIds = new HashSet<>();
        ordered.forEach(analyzer -> scheduledIds.add(analyzer.metadata().id()));
        Set<String> cyclicIds = new HashSet<>(byId.keySet());
        cyclicIds.removeAll(scheduledIds);
        List<AnalyzerRegistry.RegisteredAnalyzer> cyclic = cyclicIds.stream()
                .map(byId::get)
                .sorted(order)
                .toList();
        return new ExecutionPlan(ordered, cyclic, analyzersWithMissingDependencies, errors);
    }

    private static AnalyzerExecution skipped(String analyzerId, AnalysisError error) {
        return new AnalyzerExecution(
                analyzerId,
                AnalyzerExecutionStatus.SKIPPED,
                Duration.ZERO,
                0,
                Optional.of(error));
    }

    private static ExecutionAttempt failedAttempt(
            String analyzerId,
            AnalyzerExecutionStatus status,
            long startNanos,
            AnalysisError error) {
        return new ExecutionAttempt(
                new AnalyzerExecution(
                        analyzerId, status, elapsed(startNanos), 0, Optional.of(error)),
                List.of(),
                Optional.of(error));
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static Duration minimum(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String safeMessage(Throwable cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }

    private record ExecutionAttempt(
            AnalyzerExecution execution,
            List<Finding> findings,
            Optional<AnalysisError> error) {}

    private record ExecutionPlan(
            List<AnalyzerRegistry.RegisteredAnalyzer> orderedAnalyzers,
            List<AnalyzerRegistry.RegisteredAnalyzer> cyclicAnalyzers,
            Set<String> analyzersWithMissingDependencies,
            List<AnalysisError> errors) {
        Set<String> cyclicAnalyzerIds() {
            return cyclicAnalyzers.stream()
                    .map(analyzer -> analyzer.metadata().id())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
}
