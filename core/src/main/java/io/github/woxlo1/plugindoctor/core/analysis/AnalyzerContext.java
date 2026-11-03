package io.github.woxlo1.plugindoctor.core.analysis;

import io.github.woxlo1.plugindoctor.core.logging.AnalysisLogger;
import java.util.Objects;

public record AnalyzerContext(
        AnalysisRequest request,
        AnalysisConfiguration configuration,
        AnalysisLogger logger) {
    public AnalyzerContext {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(logger, "logger");
    }

    public AnalyzerConfiguration analyzerConfiguration(String analyzerId) {
        return configuration.configurationFor(analyzerId);
    }
}
