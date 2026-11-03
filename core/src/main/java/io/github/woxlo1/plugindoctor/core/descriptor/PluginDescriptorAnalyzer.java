package io.github.woxlo1.plugindoctor.core.descriptor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.woxlo1.plugindoctor.core.jar.JarArchive;
import io.github.woxlo1.plugindoctor.core.jar.JarArchiveEntry;
import io.github.woxlo1.plugindoctor.core.model.Finding;
import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class PluginDescriptorAnalyzer {
    private static final String ANALYZER_ID = "descriptor.analyzer";
    private static final ObjectMapper JSON_MAPPER = createJsonMapper();

    public DescriptorAnalysis analyze(JarArchive archive) {
        Objects.requireNonNull(archive, "archive");
        List<Finding> findings = new ArrayList<>();
        for (String duplicate : archive.duplicateEntryNames()) {
            findings.add(finding(
                    "jar.duplicate-entry",
                    Severity.HIGH,
                    "Duplicate archive entry",
                    "The archive contains more than one entry with the same path.",
                    List.of("Duplicate entry: " + duplicate),
                    Optional.of(duplicate),
                    1.0));
        }

        Set<String> classPaths = new HashSet<>();
        archive.entries().stream()
                .filter(entry -> !entry.directory() && entry.path().endsWith(".class"))
                .map(JarArchiveEntry::path)
                .forEach(classPaths::add);

        List<PluginDescriptor> descriptors = new ArrayList<>();
        for (DescriptorType type : DescriptorType.values()) {
            String content = archive.textEntries().get(type.path());
            if (content == null) {
                continue;
            }
            Map<String, Object> values = parse(type, content, findings);
            if (values == null) {
                continue;
            }
            PluginDescriptor descriptor = createDescriptor(
                    type, values, classPaths, findings);
            descriptors.add(descriptor);
        }
        return new DescriptorAnalysis(descriptors, findings);
    }

    private static Map<String, Object> parse(
            DescriptorType type, String content, List<Finding> findings) {
        try {
            if (type == DescriptorType.VELOCITY) {
                JsonNode root = JSON_MAPPER.readTree(content);
                if (root == null || !root.isObject()) {
                    throw new IllegalArgumentException("Descriptor root must be a JSON object.");
                }
                Map<String, Object> values = new LinkedHashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode value = field.getValue();
                    if (value.isTextual()) {
                        values.put(field.getKey(), value.textValue());
                    } else if (value.isBoolean()) {
                        values.put(field.getKey(), value.booleanValue());
                    } else if (value.isArray()) {
                        List<Object> items = new ArrayList<>();
                        value.forEach(item -> items.add(
                                item.isTextual() ? item.textValue() : item));
                        values.put(field.getKey(), items);
                    } else {
                        values.put(field.getKey(), value);
                    }
                }
                return values;
            }

            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(20);
            options.setNestingDepthLimit(50);
            options.setCodePointLimit(1024 * 1024);
            Object loaded = new Yaml(new SafeConstructor(options)).load(content);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Descriptor root must be a YAML mapping.");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Descriptor keys must be strings.");
                }
                values.put(key, entry.getValue());
            }
            return values;
        } catch (IOException | RuntimeException exception) {
            findings.add(finding(
                    "descriptor.invalid",
                    Severity.HIGH,
                    "Invalid plugin descriptor",
                    "The descriptor could not be parsed as a safe "
                            + (type == DescriptorType.VELOCITY ? "JSON object." : "YAML mapping."),
                    List.of("Descriptor: " + type.path()),
                    Optional.of(type.path()),
                    1.0));
            return null;
        }
    }

    private static PluginDescriptor createDescriptor(
            DescriptorType type,
            Map<String, Object> values,
            Set<String> classPaths,
            List<Finding> findings) {
        Optional<String> id = stringField(values, "id", type, findings);
        Optional<String> name = stringField(values, "name", type, findings);
        Optional<String> version = stringField(values, "version", type, findings);
        Optional<String> mainClass = stringField(values, "main", type, findings);
        Optional<String> apiVersion = stringField(values, "api-version", type, findings);
        Optional<String> description = stringField(values, "description", type, findings);
        Optional<String> website = stringField(values, "website", type, findings);

        requireField(type, "name", name, findings);
        requireField(type, "version", version, findings);
        requireField(type, "main", mainClass, findings);

        if (mainClass.isPresent()) {
            if (!isJavaClassName(mainClass.get())) {
                findings.add(finding(
                        "descriptor.invalid-main-class",
                        Severity.HIGH,
                        "Invalid main class name",
                        "The descriptor main value is not a valid qualified Java class name.",
                        List.of("main: " + mainClass.get()),
                        Optional.of(type.path()),
                        1.0));
            } else if (!containsMainClass(classPaths, mainClass.get())) {
                findings.add(finding(
                        "descriptor.main-class-not-found",
                        Severity.HIGH,
                        "Main class is missing",
                        "The declared main class is not present in the archive inventory.",
                        List.of("main: " + mainClass.get()),
                        Optional.of(type.path()),
                        1.0));
            }
        }

        List<String> authors = stringList(values, "authors", type, findings);
        List<String> dependencies = stringList(values, "depend", type, findings);
        List<String> softDependencies = stringList(values, "softdepend", type, findings);
        List<String> loadBefore = stringList(values, "loadbefore", type, findings);
        List<String> libraries = stringList(values, "libraries", type, findings);
        Set<String> declaredSections = new LinkedHashSet<>();
        for (String section : List.of("commands", "permissions", "aliases")) {
            if (values.containsKey(section)) {
                declaredSections.add(section);
            }
        }
        Optional<Boolean> foliaSupported = booleanField(values, "folia-supported", type, findings);

        return new PluginDescriptor(
                type,
                type.path(),
                id,
                name,
                version,
                mainClass,
                apiVersion,
                description,
                website,
                authors,
                dependencies,
                softDependencies,
                loadBefore,
                libraries,
                declaredSections,
                foliaSupported);
    }

    private static Optional<String> stringField(
            Map<String, Object> values,
            String key,
            DescriptorType type,
            List<Finding> findings) {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (key.equals("version") && value instanceof Number number) {
            return Optional.of(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        findings.add(invalidField(type, key, "a non-empty string"));
        return Optional.empty();
    }

    private static List<String> stringList(
            Map<String, Object> values,
            String key,
            DescriptorType type,
            List<Finding> findings) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String text && !text.isBlank()) {
                    strings.add(text);
                } else {
                    findings.add(invalidField(type, key, "a string or list of non-empty strings"));
                    return List.of();
                }
            }
            return List.copyOf(strings);
        }
        findings.add(invalidField(type, key, "a string or list of non-empty strings"));
        return List.of();
    }

    private static Optional<Boolean> booleanField(
            Map<String, Object> values,
            String key,
            DescriptorType type,
            List<Finding> findings) {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Boolean supported) {
            return Optional.of(supported);
        }
        findings.add(invalidField(type, key, "a boolean"));
        return Optional.empty();
    }

    private static void requireField(
            DescriptorType type,
            String field,
            Optional<String> value,
            List<Finding> findings) {
        if (value.isEmpty()) {
            findings.add(finding(
                    "descriptor.missing-required-field",
                    Severity.HIGH,
                    "Required descriptor field is missing",
                    "The descriptor does not provide a required field.",
                    List.of("Missing field: " + field),
                    Optional.of(type.path()),
                    1.0));
        }
    }

    private static Finding invalidField(
            DescriptorType type, String field, String expectedType) {
        return finding(
                "descriptor.invalid-field",
                Severity.HIGH,
                "Invalid descriptor field",
                "The descriptor field has an invalid value type.",
                List.of(field + " must be " + expectedType),
                Optional.of(type.path()),
                1.0);
    }

    private static boolean containsMainClass(Set<String> classPaths, String className) {
        String expectedPath = className.replace('.', '/') + ".class";
        if (classPaths.contains(expectedPath)) {
            return true;
        }
        String multiReleaseSuffix = "/" + expectedPath;
        return classPaths.stream().anyMatch(path -> {
            if (!path.startsWith("META-INF/versions/") || !path.endsWith(multiReleaseSuffix)) {
                return false;
            }
            String version = path.substring(
                    "META-INF/versions/".length(),
                    path.length() - multiReleaseSuffix.length());
            return version.chars().allMatch(Character::isDigit);
        });
    }

    private static boolean isJavaClassName(String className) {
        String[] parts = className.split("\\.", -1);
        if (parts.length == 0) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || !SourceVersion.isIdentifier(part) || SourceVersion.isKeyword(part)) {
                return false;
            }
        }
        return true;
    }

    private static Finding finding(
            String ruleId,
            Severity severity,
            String title,
            String description,
            List<String> evidence,
            Optional<String> affectedFile,
            double confidence) {
        return new Finding(
                ruleId,
                ANALYZER_ID,
                severity,
                "descriptor",
                title,
                description,
                evidence,
                Optional.empty(),
                Optional.empty(),
                affectedFile,
                Optional.empty(),
                Optional.empty(),
                confidence,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ObjectMapper createJsonMapper() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(50)
                        .maxStringLength(1024 * 1024)
                        .build())
                .build();
        return new ObjectMapper(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
}
