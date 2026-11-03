package io.github.woxlo1.plugindoctor.core.analysis;

public interface PluginAnalyzer {
    AnalyzerMetadata metadata();

    default void initialize(AnalyzerContext context) throws Exception {}

    AnalyzerResult analyze(AnalyzerContext context) throws Exception;

    default void close() throws Exception {}
}
