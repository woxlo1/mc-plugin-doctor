package io.github.woxlo1.plugindoctor.core.logging;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JdkAnalysisLogger implements AnalysisLogger {
    private final Logger logger;

    public JdkAnalysisLogger(Class<?> owner) {
        this(Logger.getLogger(Objects.requireNonNull(owner, "owner").getName()));
    }

    public JdkAnalysisLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void debug(String message) {
        logger.log(Level.FINE, Objects.requireNonNull(message, "message"));
    }

    @Override
    public void info(String message) {
        logger.log(Level.INFO, Objects.requireNonNull(message, "message"));
    }

    @Override
    public void warn(String message) {
        logger.log(Level.WARNING, Objects.requireNonNull(message, "message"));
    }

    @Override
    public void error(String message, Throwable cause) {
        logger.log(
                Level.SEVERE,
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(cause, "cause"));
    }
}
