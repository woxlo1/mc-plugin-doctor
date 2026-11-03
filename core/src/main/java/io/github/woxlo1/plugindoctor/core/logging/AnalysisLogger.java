package io.github.woxlo1.plugindoctor.core.logging;

public interface AnalysisLogger {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);
}
