package io.github.woxlo1.plugindoctor.core.analysis;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AnalyzerRegistry {
    private final Map<String, RegisteredAnalyzer> analyzers;

    public AnalyzerRegistry(Collection<? extends PluginAnalyzer> analyzers) {
        Objects.requireNonNull(analyzers, "analyzers");
        Map<String, RegisteredAnalyzer> registered = new LinkedHashMap<>();
        for (PluginAnalyzer analyzer : analyzers) {
            Objects.requireNonNull(analyzer, "analyzer");
            AnalyzerMetadata metadata = Objects.requireNonNull(analyzer.metadata(), "analyzer metadata");
            RegisteredAnalyzer previous = registered.putIfAbsent(
                    metadata.id(), new RegisteredAnalyzer(analyzer, metadata));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate analyzer ID: " + metadata.id());
            }
        }
        this.analyzers = Map.copyOf(registered);
    }

    public List<AnalyzerMetadata> metadata() {
        return analyzers.values().stream().map(RegisteredAnalyzer::metadata).toList();
    }

    public Optional<AnalyzerMetadata> metadata(String analyzerId) {
        return Optional.ofNullable(analyzers.get(analyzerId)).map(RegisteredAnalyzer::metadata);
    }

    int size() {
        return analyzers.size();
    }

    List<RegisteredAnalyzer> findAll() {
        return List.copyOf(analyzers.values());
    }

    Optional<RegisteredAnalyzer> find(String analyzerId) {
        return Optional.ofNullable(analyzers.get(analyzerId));
    }

    record RegisteredAnalyzer(PluginAnalyzer analyzer, AnalyzerMetadata metadata) {
        RegisteredAnalyzer {
            Objects.requireNonNull(analyzer, "analyzer");
            Objects.requireNonNull(metadata, "metadata");
        }
    }
}
