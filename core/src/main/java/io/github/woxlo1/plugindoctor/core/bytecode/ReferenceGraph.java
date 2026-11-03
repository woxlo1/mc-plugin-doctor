package io.github.woxlo1.plugindoctor.core.bytecode;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ReferenceGraph(
        List<BytecodeNode> nodes,
        List<BytecodeReference> edges) {
    public ReferenceGraph {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        Set<String> nodeIds = new HashSet<>();
        if (nodes.stream().anyMatch(node -> !nodeIds.add(node.id()))) {
            throw new IllegalArgumentException("Reference graph node IDs must be unique");
        }
        if (edges.stream().anyMatch(edge ->
                !nodeIds.contains(edge.sourceId()) || !nodeIds.contains(edge.targetId()))) {
            throw new IllegalArgumentException("Reference graph edges must refer to existing nodes");
        }
    }
}
