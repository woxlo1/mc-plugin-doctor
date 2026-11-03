package io.github.woxlo1.plugindoctor.core.model;

public enum Severity {
    INFO(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public boolean isAtLeast(Severity threshold) {
        return weight >= threshold.weight;
    }
}
