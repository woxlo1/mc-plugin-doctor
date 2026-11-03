package io.github.woxlo1.plugindoctor.core.dependency;

import io.github.woxlo1.plugindoctor.core.descriptor.PluginDescriptor;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class DependencyAnalysisService {
    private static final String ANALYZER_ID = "dependency.analyzer";
    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";

    public DependencyAnalysisReport analyze(Collection<JarAnalysisReport> reports) {
        Objects.requireNonNull(reports, "reports");
        List<Artifact> artifacts = reports.stream()
                .map(report -> new Artifact(Objects.requireNonNull(report, "report")))
                .sorted(Comparator.comparing(artifact -> artifact.id))
                .toList();
        ensureUniqueArtifacts(artifacts);

        List<Finding> findings = new ArrayList<>();
        Map<String, List<Artifact>> aliases = buildAliasIndex(artifacts);
        detectPluginConflicts(aliases, findings);

        List<DependencyEdge> edges = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            addEdges(artifact, aliases, edges, findings);
        }
        detectDependencyCycles(artifacts, edges, findings);
        detectDuplicateClasses(artifacts, findings);

        List<DependencyNode> nodes = artifacts.stream()
                .map(Artifact::node)
                .toList();
        edges.sort(Comparator.comparing(DependencyEdge::sourceArtifactId)
                .thenComparing(edge -> edge.kind().ordinal())
                .thenComparing(DependencyEdge::targetName, String.CASE_INSENSITIVE_ORDER));
        return new DependencyAnalysisReport(
                new DependencyGraph(nodes, edges),
                findings);
    }

    private static void ensureUniqueArtifacts(List<Artifact> artifacts) {
        Set<String> seen = new HashSet<>();
        for (Artifact artifact : artifacts) {
            if (!seen.add(artifact.id)) {
                throw new IllegalArgumentException(
                        "Analysis reports must have unique source paths: " + artifact.id);
            }
        }
    }

    private static Map<String, List<Artifact>> buildAliasIndex(List<Artifact> artifacts) {
        Map<String, Set<Artifact>> index = new TreeMap<>();
        for (Artifact artifact : artifacts) {
            for (String alias : artifact.aliases) {
                index.computeIfAbsent(normalize(alias), ignored -> new LinkedHashSet<>())
                        .add(artifact);
            }
        }
        Map<String, List<Artifact>> result = new LinkedHashMap<>();
        index.forEach((key, value) -> result.put(
                key, value.stream().sorted(Comparator.comparing(artifact -> artifact.id)).toList()));
        return result;
    }

    private static void detectPluginConflicts(
            Map<String, List<Artifact>> aliases, List<Finding> findings) {
        Map<String, List<String>> conflictingAliases = new TreeMap<>();
        Map<String, List<Artifact>> conflictingArtifacts = new HashMap<>();
        aliases.forEach((alias, matches) -> {
            if (matches.size() < 2) {
                return;
            }
            String key = matches.stream()
                    .map(artifact -> artifact.id)
                    .sorted()
                    .reduce((left, right) -> left + "\u0000" + right)
                    .orElseThrow();
            conflictingAliases.computeIfAbsent(key, ignored -> new ArrayList<>()).add(alias);
            conflictingArtifacts.putIfAbsent(key, matches);
        });
        conflictingAliases.forEach((key, duplicateAliases) -> {
            List<Artifact> matches = conflictingArtifacts.get(key);
            List<String> evidence = new ArrayList<>();
            evidence.add("Plugin identities: " + String.join(", ", duplicateAliases));
            matches.forEach(artifact -> evidence.add(
                    artifact.id + " (version: " + artifact.version.orElse("unknown") + ")"));
            findings.add(finding(
                    "dependency.duplicate-plugin",
                    Severity.HIGH,
                    "Plugin identity is provided by multiple artifacts",
                    "More than one analyzed artifact declares the same plugin identity.",
                    evidence,
                    Optional.empty()));

            Set<String> versions = new HashSet<>();
            matches.stream().map(artifact -> artifact.version)
                    .flatMap(Optional::stream)
                    .map(DependencyAnalysisService::normalize)
                    .forEach(versions::add);
            if (versions.size() > 1) {
                findings.add(finding(
                        "dependency.version-conflict",
                        Severity.HIGH,
                        "Conflicting plugin versions",
                        "Artifacts with the same plugin identity declare different versions.",
                        evidence,
                        Optional.empty()));
            }
        });
    }

    private static void addEdges(
            Artifact artifact,
            Map<String, List<Artifact>> aliases,
            List<DependencyEdge> edges,
            List<Finding> findings) {
        for (String dependency : artifact.requiredDependencies) {
            List<Artifact> targets = resolve(aliases, dependency);
            edges.add(edge(artifact, dependency, DependencyKind.REQUIRED, targets));
            if (targets.isEmpty()) {
                findings.add(finding(
                        "dependency.missing-required",
                        Severity.HIGH,
                        "Required plugin dependency is missing",
                        "No analyzed artifact provides a required plugin dependency.",
                        List.of("Plugin: " + artifact.name, "Missing dependency: " + dependency),
                        Optional.of(artifact.source.toString())));
            }
        }
        for (String dependency : artifact.optionalDependencies) {
            edges.add(edge(
                    artifact,
                    dependency,
                    DependencyKind.OPTIONAL,
                    resolve(aliases, dependency)));
        }
        for (String target : artifact.loadBefore) {
            edges.add(edge(
                    artifact,
                    target,
                    DependencyKind.LOAD_BEFORE,
                    resolve(aliases, target)));
        }
    }

    private static List<Artifact> resolve(
            Map<String, List<Artifact>> aliases, String dependency) {
        return aliases.getOrDefault(normalize(dependency), List.of());
    }

    private static DependencyEdge edge(
            Artifact source,
            String targetName,
            DependencyKind kind,
            List<Artifact> targets) {
        return new DependencyEdge(
                source.id,
                targetName,
                kind,
                targets.stream().map(artifact -> artifact.id).toList());
    }

    private static void detectDependencyCycles(
            List<Artifact> artifacts,
            List<DependencyEdge> edges,
            List<Finding> findings) {
        Map<String, List<String>> adjacency = new TreeMap<>();
        Map<String, String> namesById = new HashMap<>();
        artifacts.forEach(artifact -> {
            adjacency.put(artifact.id, new ArrayList<>());
            namesById.put(artifact.id, artifact.name);
        });
        edges.stream()
                .filter(edge -> edge.kind() == DependencyKind.REQUIRED)
                .forEach(edge -> edge.targetArtifactIds().forEach(target ->
                        adjacency.get(edge.sourceArtifactId()).add(target)));
        adjacency.values().forEach(targets -> targets.sort(String::compareTo));

        StronglyConnectedComponents components = new StronglyConnectedComponents(adjacency);
        for (List<String> component : components.find()) {
            boolean selfCycle = component.size() == 1
                    && adjacency.get(component.get(0)).contains(component.get(0));
            if (component.size() < 2 && !selfCycle) {
                continue;
            }
            List<String> names = component.stream()
                    .map(namesById::get)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<String> evidence = new ArrayList<>();
            evidence.add("Dependency cycle: " + String.join(" -> ", names));
            component.forEach(evidence::add);
            findings.add(finding(
                    "dependency.cycle",
                    Severity.HIGH,
                    "Required plugin dependencies contain a cycle",
                    "The required dependency graph contains a cycle that prevents a valid load order.",
                    evidence,
                    Optional.empty()));
        }
    }

    private static void detectDuplicateClasses(
            List<Artifact> artifacts, List<Finding> findings) {
        Map<String, Set<String>> ownersByClass = new TreeMap<>();
        for (Artifact artifact : artifacts) {
            for (String classPath : artifact.classEntries) {
                String className = canonicalClassName(classPath);
                if (className != null) {
                    ownersByClass.computeIfAbsent(className, ignored -> new TreeSet<>())
                            .add(artifact.id);
                }
            }
        }
        ownersByClass.forEach((className, owners) -> {
            if (owners.size() < 2) {
                return;
            }
            List<String> evidence = new ArrayList<>();
            evidence.add("Class: " + className);
            owners.forEach(evidence::add);
            findings.add(finding(
                    "dependency.duplicate-class",
                    Severity.HIGH,
                    "Class is present in multiple plugin artifacts",
                    "Multiple analyzed artifacts contain the same logical class.",
                    evidence,
                    Optional.of(className)));
        });
    }

    private static String canonicalClassName(String classPath) {
        String path = classPath;
        if (path.startsWith(MULTI_RELEASE_PREFIX)) {
            int versionEnd = path.indexOf('/', MULTI_RELEASE_PREFIX.length());
            if (versionEnd < 0
                    || !path.substring(MULTI_RELEASE_PREFIX.length(), versionEnd)
                            .chars().allMatch(Character::isDigit)) {
                return null;
            }
            path = path.substring(versionEnd + 1);
        }
        if (!path.endsWith(".class")) {
            return null;
        }
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    private static Finding finding(
            String ruleId,
            Severity severity,
            String title,
            String description,
            List<String> evidence,
            Optional<String> affectedFileOrClass) {
        return new Finding(
                ruleId,
                ANALYZER_ID,
                severity,
                "dependency",
                title,
                description,
                evidence,
                ruleId.equals("dependency.duplicate-class")
                        ? affectedFileOrClass : Optional.empty(),
                Optional.empty(),
                ruleId.equals("dependency.duplicate-class")
                        ? Optional.empty() : affectedFileOrClass,
                Optional.empty(),
                Optional.empty(),
                1.0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static final class Artifact {
        private final String id;
        private final Path source;
        private final String name;
        private final Optional<String> version;
        private final Set<String> aliases;
        private final List<String> requiredDependencies;
        private final List<String> optionalDependencies;
        private final List<String> loadBefore;
        private final List<String> classEntries;

        private Artifact(JarAnalysisReport report) {
            source = report.source();
            id = source.toString();
            List<PluginDescriptor> descriptors = report.descriptors();
            Optional<String> descriptorName = descriptors.stream()
                    .map(PluginDescriptor::name)
                    .flatMap(Optional::stream)
                    .findFirst();
            Optional<String> descriptorId = descriptors.stream()
                    .map(PluginDescriptor::id)
                    .flatMap(Optional::stream)
                    .findFirst();
            name = descriptorName.or(() -> descriptorId)
                    .orElseGet(() -> fileName(source));
            version = descriptors.stream()
                    .map(PluginDescriptor::version)
                    .flatMap(Optional::stream)
                    .findFirst();

            Set<String> identityAliases = new LinkedHashSet<>();
            descriptors.forEach(descriptor -> {
                descriptor.id().ifPresent(identityAliases::add);
                descriptor.name().ifPresent(identityAliases::add);
            });
            aliases = Set.copyOf(identityAliases);
            requiredDependencies = distinctDependencies(descriptors, PluginDescriptor::dependencies);
            optionalDependencies =
                    distinctDependencies(descriptors, PluginDescriptor::softDependencies);
            loadBefore = distinctDependencies(descriptors, PluginDescriptor::loadBefore);
            classEntries = report.classEntries();
        }

        private DependencyNode node() {
            return new DependencyNode(id, name, version, source);
        }
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static List<String> distinctDependencies(
            List<PluginDescriptor> descriptors,
            java.util.function.Function<PluginDescriptor, List<String>> selector) {
        Map<String, String> unique = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        descriptors.stream()
                .map(selector)
                .flatMap(Collection::stream)
                .forEach(value -> unique.putIfAbsent(normalize(value), value));
        return List.copyOf(unique.values());
    }

    private static final class StronglyConnectedComponents {
        private final Map<String, List<String>> adjacency;

        private StronglyConnectedComponents(Map<String, List<String>> adjacency) {
            this.adjacency = adjacency;
        }

        private List<List<String>> find() {
            Set<String> visited = new HashSet<>();
            List<String> finishOrder = new ArrayList<>();
            for (String root : adjacency.keySet()) {
                if (!visited.add(root)) {
                    continue;
                }
                ArrayDeque<TraversalFrame> frames = new ArrayDeque<>();
                frames.push(new TraversalFrame(root));
                while (!frames.isEmpty()) {
                    TraversalFrame frame = frames.peek();
                    List<String> targets = adjacency.get(frame.node);
                    if (frame.nextTargetIndex < targets.size()) {
                        String target = targets.get(frame.nextTargetIndex++);
                        if (visited.add(target)) {
                            frames.push(new TraversalFrame(target));
                        }
                    } else {
                        finishOrder.add(frames.pop().node);
                    }
                }
            }

            Map<String, List<String>> reverse = new TreeMap<>();
            adjacency.keySet().forEach(node -> reverse.put(node, new ArrayList<>()));
            adjacency.forEach((source, targets) -> targets.forEach(target ->
                    reverse.get(target).add(source)));
            reverse.values().forEach(Collections::sort);

            visited.clear();
            List<List<String>> components = new ArrayList<>();
            for (int index = finishOrder.size() - 1; index >= 0; index--) {
                String root = finishOrder.get(index);
                if (!visited.add(root)) {
                    continue;
                }
                List<String> component = new ArrayList<>();
                ArrayDeque<String> pending = new ArrayDeque<>();
                pending.push(root);
                while (!pending.isEmpty()) {
                    String member = pending.pop();
                    component.add(member);
                    for (String predecessor : reverse.get(member)) {
                        if (visited.add(predecessor)) {
                            pending.push(predecessor);
                        }
                    }
                }
                component.sort(String::compareTo);
                components.add(List.copyOf(component));
            }
            return List.copyOf(components);
        }

        private static final class TraversalFrame {
            private final String node;
            private int nextTargetIndex;

            private TraversalFrame(String node) {
                this.node = node;
            }
        }
    }
}
