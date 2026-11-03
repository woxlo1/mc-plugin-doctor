package io.github.woxlo1.plugindoctor.core.compatibility;

import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.util.List;
import java.util.Objects;

public record ClassFileScanResult(
        List<ClassFileInfo> classes,
        List<Finding> findings) {
    public ClassFileScanResult {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
}
