package io.github.woxlo1.plugindoctor.core.bytecode;

import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record BytecodeAnalysisReport(
        Path source,
        List<BytecodeClassInfo> classes,
        List<BytecodeMethodInfo> methods,
        List<BytecodeFieldInfo> fields,
        ReferenceGraph referenceGraph,
        List<Finding> findings) {
    public BytecodeAnalysisReport {
        source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        Objects.requireNonNull(referenceGraph, "referenceGraph");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
}
