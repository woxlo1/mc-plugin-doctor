package io.github.woxlo1.plugindoctor.core.dependency;

import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.util.List;
import java.util.Objects;

public record DependencyAnalysisReport(
        DependencyGraph graph,
        List<Finding> findings) {
    public DependencyAnalysisReport {
        Objects.requireNonNull(graph, "graph");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
}
