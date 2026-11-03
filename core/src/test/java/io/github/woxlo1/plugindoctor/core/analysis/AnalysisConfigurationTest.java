package io.github.woxlo1.plugindoctor.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisConfigurationTest {
    @Test
    void resolvesDefaultsAndPerAnalyzerOverrides() {
        AnalysisConfiguration configuration = new AnalysisConfiguration(
                Duration.ofSeconds(10),
                Severity.LOW,
                false,
                Map.of("analyzer.example", new AnalyzerConfiguration(
                        false, Map.of("mode", "strict"), Optional.of(Duration.ofSeconds(2)))));

        assertEquals(Duration.ofSeconds(10), configuration.timeoutFor("unknown"));
        assertEquals(Duration.ofSeconds(2), configuration.timeoutFor("analyzer.example"));
        assertFalse(configuration.configurationFor("analyzer.example").enabled());
        assertEquals("strict", configuration.configurationFor("analyzer.example").options().get("mode"));
    }

    @Test
    void rejectsNonPositiveTimeoutsAndInvalidOptionEntries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalysisConfiguration(Duration.ZERO, Severity.INFO, true, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyzerConfiguration(true, Map.of("", "value"), Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> new AnalyzerConfiguration(true, Map.of("mode", null), Optional.empty()));
    }

    @Test
    void providesUsefulDefaults() {
        AnalysisConfiguration configuration = AnalysisConfiguration.defaults();

        assertEquals(Duration.ofSeconds(30), configuration.defaultAnalyzerTimeout());
        assertEquals(Severity.INFO, configuration.minimumSeverity());
        assertEquals(AnalyzerConfiguration.defaults(), configuration.configurationFor("any"));
    }
}
