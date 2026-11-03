package io.github.woxlo1.plugindoctor.core.descriptor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record PluginDescriptor(
        DescriptorType type,
        String path,
        Optional<String> id,
        Optional<String> name,
        Optional<String> version,
        Optional<String> mainClass,
        Optional<String> apiVersion,
        Optional<String> description,
        Optional<String> website,
        List<String> authors,
        List<String> dependencies,
        List<String> softDependencies,
        List<String> loadBefore,
        List<String> libraries,
        Set<String> declaredSections,
        Optional<Boolean> foliaSupported) {
    public PluginDescriptor {
        Objects.requireNonNull(type, "type");
        path = requireText(path, "path");
        id = clean(id, "id");
        name = clean(name, "name");
        version = clean(version, "version");
        mainClass = clean(mainClass, "mainClass");
        apiVersion = clean(apiVersion, "apiVersion");
        description = clean(description, "description");
        website = clean(website, "website");
        authors = List.copyOf(Objects.requireNonNull(authors, "authors"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        softDependencies = List.copyOf(
                Objects.requireNonNull(softDependencies, "softDependencies"));
        loadBefore = List.copyOf(Objects.requireNonNull(loadBefore, "loadBefore"));
        libraries = List.copyOf(Objects.requireNonNull(libraries, "libraries"));
        declaredSections = Set.copyOf(
                Objects.requireNonNull(declaredSections, "declaredSections"));
        foliaSupported = Objects.requireNonNull(foliaSupported, "foliaSupported");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Optional<String> clean(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isPresent() && value.get().isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank when present");
        }
        return value;
    }
}
