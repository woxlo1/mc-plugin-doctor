package io.github.woxlo1.plugindoctor.core.analysis;

public interface PluginAnalyzer {
    AnalyzerMetadata metadata();

    AnalyzerResult analyze(AnalyzerContext context) throws Exception;
}
