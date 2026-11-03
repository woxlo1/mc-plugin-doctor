package io.github.woxlo1.plugindoctor.core.descriptor;

import io.github.woxlo1.plugindoctor.core.model.Finding;
import java.util.List;
import java.util.Objects;

public record DescriptorAnalysis(
        List<PluginDescriptor> descriptors,
        List<Finding> findings) {
    public DescriptorAnalysis {
        descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
}
