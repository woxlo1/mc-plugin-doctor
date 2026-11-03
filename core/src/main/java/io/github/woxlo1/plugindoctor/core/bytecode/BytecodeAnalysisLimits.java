package io.github.woxlo1.plugindoctor.core.bytecode;

public record BytecodeAnalysisLimits(
        int maximumGraphNodes,
        int maximumGraphReferences) {
    public BytecodeAnalysisLimits {
        if (maximumGraphNodes < 1) {
            throw new IllegalArgumentException("maximumGraphNodes must be positive");
        }
        if (maximumGraphReferences < 1) {
            throw new IllegalArgumentException("maximumGraphReferences must be positive");
        }
    }

    public static BytecodeAnalysisLimits defaults() {
        return new BytecodeAnalysisLimits(200_000, 500_000);
    }
}
