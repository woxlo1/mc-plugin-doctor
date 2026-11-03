package io.github.woxlo1.plugindoctor.core.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.plugindoctor.core.descriptor.DescriptorType;
import io.github.woxlo1.plugindoctor.core.descriptor.PluginDescriptor;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyAnalysisServiceTest {
    private final DependencyAnalysisService service = new DependencyAnalysisService();

    @Test
    void buildsResolvedRequiredOptionalAndLoadOrderEdges() {
        JarAnalysisReport app = report(
                "app.jar",
                descriptor("App", "1.0", List.of("cOrE"), List.of("Optional"), List.of("Later")),
                List.of());
        JarAnalysisReport core = report(
                "core.jar", descriptor("Core", "2.0", List.of(), List.of(), List.of()), List.of());
        JarAnalysisReport later = report(
                "later.jar", descriptor("Later", "1.0", List.of(), List.of(), List.of()), List.of());

        DependencyAnalysisReport result = service.analyze(List.of(app, core, later));

        assertEquals(3, result.graph().nodes().size());
        assertEquals(3, result.graph().edges().size());
        DependencyEdge required = edge(result, "App", DependencyKind.REQUIRED, "cOrE");
        assertTrue(required.resolved());
        assertEquals(1, required.targetArtifactIds().size());
        DependencyEdge optional = edge(result, "App", DependencyKind.OPTIONAL, "Optional");
        assertFalse(optional.resolved());
        DependencyEdge loadBefore = edge(result, "App", DependencyKind.LOAD_BEFORE, "Later");
        assertTrue(loadBefore.resolved());
        assertTrue(result.findings().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.graph().edges().clear());
    }

    @Test
    void reportsMissingRequiredDependenciesButNotMissingOptionalOnes() {
        JarAnalysisReport app = report(
                "app.jar",
                descriptor("App", "1.0", List.of("Missing"), List.of("Optional"), List.of()),
                List.of());

        DependencyAnalysisReport result = service.analyze(List.of(app));

        assertEquals(1, result.findings().size());
        assertEquals("dependency.missing-required", result.findings().get(0).ruleId());
        assertTrue(result.graph().edges().stream()
                .filter(edge -> edge.kind() == DependencyKind.REQUIRED)
                .noneMatch(DependencyEdge::resolved));
        assertTrue(result.graph().edges().stream()
                .filter(edge -> edge.kind() == DependencyKind.OPTIONAL)
                .noneMatch(DependencyEdge::resolved));
    }

    @Test
    void reportsDuplicateClassesAcrossArtifactsAndIgnoresVariantsWithinOneArtifact() {
        JarAnalysisReport first = report(
                "first.jar",
                descriptor("First", "1.0", List.of(), List.of(), List.of()),
                List.of("pkg/Shared.class", "META-INF/versions/17/pkg/Shared.class"));
        JarAnalysisReport second = report(
                "second.jar",
                descriptor("Second", "1.0", List.of(), List.of(), List.of()),
                List.of("pkg/Shared.class"));

        DependencyAnalysisReport result = service.analyze(List.of(first, second));

        List<String> duplicateClassFindings = result.findings().stream()
                .filter(finding -> finding.ruleId().equals("dependency.duplicate-class"))
                .map(finding -> finding.affectedClass().orElseThrow())
                .toList();
        assertEquals(List.of("pkg.Shared"), duplicateClassFindings);
    }

    @Test
    void reportsDuplicatePluginIdentitiesAndVersionConflictsCaseInsensitively() {
        JarAnalysisReport first = report(
                "first.jar",
                descriptor("Example", "1.0", List.of(), List.of(), List.of()),
                List.of());
        JarAnalysisReport second = report(
                "second.jar",
                descriptor("example", "2.0", List.of(), List.of(), List.of()),
                List.of());

        DependencyAnalysisReport result = service.analyze(List.of(first, second));

        assertEquals(
                Set.of("dependency.duplicate-plugin", "dependency.version-conflict"),
                result.findings().stream().map(finding -> finding.ruleId())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void reportsOneConflictWhenMultipleIdentityAliasesMatchTheSameArtifacts() {
        JarAnalysisReport first = report(
                "first.jar",
                descriptor("example-id", "Example", "1.0", List.of(), List.of(), List.of()),
                List.of());
        JarAnalysisReport second = report(
                "second.jar",
                descriptor("EXAMPLE-ID", "example", "2.0", List.of(), List.of(), List.of()),
                List.of());

        DependencyAnalysisReport result = service.analyze(List.of(first, second));

        assertEquals(1, result.findings().stream()
                .filter(finding -> finding.ruleId().equals("dependency.duplicate-plugin"))
                .count());
        assertEquals(1, result.findings().stream()
                .filter(finding -> finding.ruleId().equals("dependency.version-conflict"))
                .count());
    }

    @Test
    void reportsRequiredDependencyCyclesAndLeavesOptionalCyclesAsNonFatal() {
        JarAnalysisReport first = report(
                "first.jar",
                descriptor("First", "1.0", List.of("Second"), List.of(), List.of()),
                List.of());
        JarAnalysisReport second = report(
                "second.jar",
                descriptor("Second", "1.0", List.of("First"), List.of(), List.of()),
                List.of());

        DependencyAnalysisReport result = service.analyze(List.of(first, second));

        assertEquals(1, result.findings().size());
        assertEquals("dependency.cycle", result.findings().get(0).ruleId());
    }

    @Test
    void rejectsDuplicateReportSourcesInsteadOfProducingAmbiguousGraphNodes() {
        JarAnalysisReport report = report(
                "same.jar",
                descriptor("Same", "1.0", List.of(), List.of(), List.of()),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> service.analyze(List.of(report, report)));
    }

    @Test
    void acceptsAnEmptyArtifactCollection() {
        DependencyAnalysisReport result = service.analyze(List.of());

        assertTrue(result.graph().nodes().isEmpty());
        assertTrue(result.graph().edges().isEmpty());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void doesNotResolvePluginDependenciesToArtifactsWithoutPluginIdentity() {
        JarAnalysisReport app = report(
                "app.jar",
                descriptor("App", "1.0", List.of("helper"), List.of(), List.of()),
                List.of());
        JarAnalysisReport helperLibrary = new JarAnalysisReport(
                Path.of("test-artifacts", "helper.jar"),
                0,
                List.of(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        DependencyAnalysisReport result = service.analyze(List.of(app, helperLibrary));

        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.ruleId().equals("dependency.missing-required")));
    }

    private static DependencyEdge edge(
            DependencyAnalysisReport report,
            String sourceName,
            DependencyKind kind,
            String targetName) {
        String sourceId = report.graph().nodes().stream()
                .filter(node -> node.name().equals(sourceName))
                .map(DependencyNode::artifactId)
                .findFirst()
                .orElseThrow();
        return report.graph().edges().stream()
                .filter(edge -> edge.sourceArtifactId().equals(sourceId))
                .filter(edge -> edge.kind() == kind && edge.targetName().equals(targetName))
                .findFirst()
                .orElseThrow();
    }

    private static JarAnalysisReport report(
            String fileName, PluginDescriptor descriptor, List<String> classEntries) {
        Path source = Path.of("test-artifacts", fileName).toAbsolutePath().normalize();
        return new JarAnalysisReport(
                source,
                0,
                List.of(),
                Optional.empty(),
                List.of(descriptor),
                classEntries,
                List.of(),
                List.of());
    }

    private static PluginDescriptor descriptor(
            String name,
            String version,
            List<String> dependencies,
            List<String> softDependencies,
            List<String> loadBefore) {
        return descriptor(
                Optional.empty(), name, version, dependencies, softDependencies, loadBefore);
    }

    private static PluginDescriptor descriptor(
            String id,
            String name,
            String version,
            List<String> dependencies,
            List<String> softDependencies,
            List<String> loadBefore) {
        return descriptor(
                Optional.of(id), name, version, dependencies, softDependencies, loadBefore);
    }

    private static PluginDescriptor descriptor(
            Optional<String> id,
            String name,
            String version,
            List<String> dependencies,
            List<String> softDependencies,
            List<String> loadBefore) {
        return new PluginDescriptor(
                DescriptorType.BUKKIT,
                DescriptorType.BUKKIT.path(),
                id,
                Optional.of(name),
                Optional.of(version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                dependencies,
                softDependencies,
                loadBefore,
                List.of(),
                Set.of(),
                Optional.empty());
    }
}
