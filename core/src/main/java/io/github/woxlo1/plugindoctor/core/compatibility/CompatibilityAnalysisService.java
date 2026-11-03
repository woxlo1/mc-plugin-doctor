package io.github.woxlo1.plugindoctor.core.compatibility;

import io.github.woxlo1.plugindoctor.core.descriptor.PluginDescriptor;
import io.github.woxlo1.plugindoctor.core.jar.JarAnalysisReport;
import io.github.woxlo1.plugindoctor.core.jar.ManifestInfo;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CompatibilityAnalysisService {
    private static final String ANALYZER_ID = "compatibility.analyzer";
    private static final int MAXIMUM_VERSION_LENGTH = 128;
    private static final Pattern NUMERIC_VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)*");
    private final ClassFileScanner classFileScanner;

    public CompatibilityAnalysisService() {
        this(new ClassFileScanner());
    }

    public CompatibilityAnalysisService(ClassFileScanner classFileScanner) {
        this.classFileScanner = Objects.requireNonNull(classFileScanner, "classFileScanner");
    }

    public CompatibilityReport analyze(
            JarAnalysisReport report, PlatformCompatibilityProfile profile) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(profile, "profile");
        ClassFileScanResult scan = classFileScanner.scan(report);
        List<Finding> findings = new ArrayList<>(scan.findings());
        List<ClassFileInfo> effectiveClasses = effectiveClasses(
                scan.classes(), profile.javaRelease(), isMultiReleaseJar(report));

        int highestJavaRelease = 0;
        for (ClassFileInfo classFile : effectiveClasses) {
            int classJavaRelease = javaReleaseForMajorVersion(classFile.majorVersion());
            highestJavaRelease = Math.max(highestJavaRelease, classJavaRelease);
            if (classJavaRelease > profile.javaRelease()) {
                findings.add(finding(
                        "compatibility.java-version-too-new",
                        Severity.HIGH,
                        "Plugin class requires a newer Java runtime",
                        "The class-file version is newer than the target platform Java runtime.",
                        List.of(
                                "Class: " + classFile.className(),
                                "Class Java release: " + displayJavaRelease(classJavaRelease),
                                "Target Java release: " + displayJavaRelease(profile.javaRelease())),
                        Optional.of(classFile.className()),
                        Optional.of(classFile.entryPath()),
                        profile));
            }
            if (classFile.usesPreviewFeatures() && classJavaRelease != profile.javaRelease()) {
                findings.add(finding(
                        "compatibility.preview-java-mismatch",
                        Severity.HIGH,
                        "Preview class targets a different Java release",
                        "Preview class files can only be loaded by their matching Java release.",
                        List.of(
                                "Class: " + classFile.className(),
                                "Preview Java release: " + displayJavaRelease(classJavaRelease),
                                "Target Java release: " + displayJavaRelease(profile.javaRelease())),
                        Optional.of(classFile.className()),
                        Optional.of(classFile.entryPath()),
                        profile));
            }
        }

        report.descriptors().forEach(descriptor ->
                checkDescriptor(descriptor, profile, findings));
        Map<ApiReference, ApiCatalogEntry> catalog = indexCatalog(profile.apiCatalog());
        Map<ApiReference, List<ClassFileInfo>> usages =
                collectCatalogUsages(effectiveClasses, catalog);
        usages.forEach((reference, owners) -> {
            ApiCatalogEntry entry = catalog.get(reference);
            if (entry.status() == ApiStatus.AVAILABLE) {
                return;
            }
            boolean removed = entry.status() == ApiStatus.REMOVED;
            List<String> evidence = new ArrayList<>();
            evidence.add("Platform: " + profile.platform() + " " + profile.apiVersion());
            evidence.add("API reference: " + format(reference));
            owners.stream()
                    .map(ClassFileInfo::entryPath)
                    .sorted()
                    .forEach(path -> evidence.add("Referenced from: " + path));
            entry.replacement().ifPresent(replacement ->
                    evidence.add("Replacement: " + replacement));
            findings.add(finding(
                    removed ? "compatibility.removed-api" : "compatibility.deprecated-api",
                    removed ? Severity.HIGH : Severity.MEDIUM,
                    removed ? "Removed platform API is referenced" : "Deprecated platform API is referenced",
                    removed
                            ? "The platform API catalog marks this reference as removed."
                            : "The platform API catalog marks this reference as deprecated.",
                    evidence,
                    Optional.of(reference.owner()),
                    Optional.empty(),
                    profile));
        });

        List<ApiReference> apiReferences = effectiveClasses.stream()
                .flatMap(classFile -> classFile.apiReferences().stream())
                .distinct()
                .sorted(apiReferenceComparator())
                .toList();
        return new CompatibilityReport(
                profile.platform(),
                profile.apiVersion(),
                profile.javaRelease(),
                highestJavaRelease == 0 ? Optional.empty() : Optional.of(highestJavaRelease),
                buildJdk(report.manifest()),
                scan.classes(),
                apiReferences,
                findings);
    }

    private static void checkDescriptor(
            PluginDescriptor descriptor,
            PlatformCompatibilityProfile profile,
            List<Finding> findings) {
        if (!profile.supportedDescriptors().contains(descriptor.type())) {
            findings.add(finding(
                    "compatibility.unsupported-descriptor",
                    Severity.HIGH,
                    "Plugin descriptor is not supported by the target platform",
                    "The target platform profile does not support this descriptor type.",
                    List.of(
                            "Descriptor: " + descriptor.path(),
                            "Platform: " + profile.platform()),
                    Optional.empty(),
                    Optional.of(descriptor.path()),
                    profile));
        }
        descriptor.apiVersion().ifPresent(apiVersion -> {
            Optional<Integer> comparison = compareNumericVersions(apiVersion, profile.apiVersion());
            if (comparison.isPresent() && comparison.get() > 0) {
                findings.add(finding(
                        "compatibility.api-version-newer-than-target",
                        Severity.HIGH,
                        "Plugin requires a newer platform API",
                        "The descriptor API version is newer than the target platform API.",
                        List.of(
                                "Plugin API version: " + apiVersion,
                                "Target API version: " + profile.apiVersion(),
                                "Platform: " + profile.platform()),
                        Optional.empty(),
                        Optional.of(descriptor.path()),
                        profile));
            }
        });
    }

    private static Map<ApiReference, ApiCatalogEntry> indexCatalog(
            List<ApiCatalogEntry> entries) {
        Map<ApiReference, ApiCatalogEntry> result = new HashMap<>();
        entries.forEach(entry -> result.put(entry.reference(), entry));
        return result;
    }

    private static Map<ApiReference, List<ClassFileInfo>> collectCatalogUsages(
            List<ClassFileInfo> classes, Map<ApiReference, ApiCatalogEntry> catalog) {
        Map<ApiReference, List<ClassFileInfo>> usages = new HashMap<>();
        for (ClassFileInfo classFile : classes) {
            for (ApiReference reference : classFile.apiReferences()) {
                if (catalog.containsKey(reference)) {
                    usages.computeIfAbsent(reference, ignored -> new ArrayList<>()).add(classFile);
                }
            }
        }
        return usages;
    }

    private static List<ClassFileInfo> effectiveClasses(
            List<ClassFileInfo> classes, int targetJavaRelease, boolean multiReleaseJar) {
        Map<String, ClassCandidate> selected = new HashMap<>();
        classes.stream()
                .sorted(Comparator.comparing(ClassFileInfo::entryPath))
                .forEach(classFile -> {
                    boolean versionedPath = classFile.entryPath().startsWith("META-INF/versions/");
                    if (versionedPath && !multiReleaseJar) {
                        return;
                    }
                    Optional<Integer> multiRelease = versionedPath
                            ? multiReleaseVersion(classFile.entryPath()) : Optional.of(0);
                    if (multiRelease.isEmpty() || multiRelease.get() > targetJavaRelease) {
                        return;
                    }
                    int release = multiRelease.get();
                    ClassCandidate current = selected.get(classFile.className());
                    if (current == null || release > current.multiReleaseVersion()) {
                        selected.put(classFile.className(), new ClassCandidate(release, classFile));
                    }
                });
        return selected.values().stream()
                .map(ClassCandidate::classFile)
                .sorted(Comparator.comparing(ClassFileInfo::entryPath))
                .toList();
    }

    private static boolean isMultiReleaseJar(JarAnalysisReport report) {
        return report.manifest()
                .map(manifest -> manifest.mainAttributes().get("Multi-Release"))
                .filter("true"::equalsIgnoreCase)
                .isPresent();
    }

    private static Optional<Integer> multiReleaseVersion(String entryPath) {
        String prefix = "META-INF/versions/";
        if (!entryPath.startsWith(prefix)) {
            return Optional.of(0);
        }
        int separator = entryPath.indexOf('/', prefix.length());
        if (separator < 0) {
            return Optional.empty();
        }
        String version = entryPath.substring(prefix.length(), separator);
        if (version.length() > 9 || !version.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        try {
            int release = Integer.parseInt(version);
            return release >= 9 ? Optional.of(release) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> buildJdk(Optional<ManifestInfo> manifest) {
        if (manifest.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> attributes = manifest.get().mainAttributes();
        return Optional.ofNullable(attributes.get("Build-Jdk-Spec"))
                .or(() -> Optional.ofNullable(attributes.get("Build-Jdk")));
    }

    private static Optional<Integer> compareNumericVersions(String left, String right) {
        if (left.length() > MAXIMUM_VERSION_LENGTH
                || right.length() > MAXIMUM_VERSION_LENGTH
                || !NUMERIC_VERSION.matcher(left).matches()
                || !NUMERIC_VERSION.matcher(right).matches()) {
            return Optional.empty();
        }
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            String leftPartText = index < leftParts.length ? leftParts[index] : "0";
            String rightPartText = index < rightParts.length ? rightParts[index] : "0";
            if (leftPartText.length() > 9 || rightPartText.length() > 9) {
                return Optional.empty();
            }
            BigInteger leftPart = new BigInteger(leftPartText);
            BigInteger rightPart = new BigInteger(rightPartText);
            int comparison = leftPart.compareTo(rightPart);
            if (comparison != 0) {
                return Optional.of(comparison);
            }
        }
        return Optional.of(0);
    }

    private static int javaReleaseForMajorVersion(int majorVersion) {
        return majorVersion == 45 ? 1 : majorVersion - 44;
    }

    private static String displayJavaRelease(int release) {
        return release == 1 ? "1.1" : Integer.toString(release);
    }

    private static String format(ApiReference reference) {
        if (reference.kind() == ApiReferenceKind.CLASS) {
            return reference.owner();
        }
        return reference.owner() + "#" + reference.name().orElseThrow()
                + reference.descriptor().orElseThrow();
    }

    private static Comparator<ApiReference> apiReferenceComparator() {
        return Comparator.comparing((ApiReference reference) -> reference.kind().ordinal())
                .thenComparing(ApiReference::owner)
                .thenComparing(reference -> reference.name().orElse(""))
                .thenComparing(reference -> reference.descriptor().orElse(""));
    }

    private static Finding finding(
            String ruleId,
            Severity severity,
            String title,
            String description,
            List<String> evidence,
            Optional<String> affectedClass,
            Optional<String> affectedFile,
            PlatformCompatibilityProfile profile) {
        return new Finding(
                ruleId,
                ANALYZER_ID,
                severity,
                "compatibility",
                title,
                description,
                evidence,
                affectedClass,
                Optional.empty(),
                affectedFile,
                Optional.empty(),
                Optional.empty(),
                1.0,
                Optional.of(profile.platform()),
                Optional.of(profile.apiVersion()),
                Optional.empty());
    }

    private record ClassCandidate(int multiReleaseVersion, ClassFileInfo classFile) {}
}
