package io.github.woxlo1.plugindoctor.core.dependency;

import java.util.List;
import java.util.Objects;

public record DependencyGraph(
        List<DependencyNode> nodes,
        List<DependencyEdge> edges) {
    public DependencyGraph {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    }
}
