package io.github.woxlo1.plugindoctor.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FindingTest {
    @Test
    void copiesEvidenceAndSupportsSeverityChanges() {
        List<String> evidence = new ArrayList<>(List.of("Observed reference"));
        Finding finding = finding(evidence);
        evidence.add("Later mutation");

        assertEquals(List.of("Observed reference"), finding.evidence());
        assertEquals(Severity.HIGH, finding.withSeverity(Severity.HIGH).severity());
        assertThrows(UnsupportedOperationException.class, () -> finding.evidence().add("Mutation"));
    }

    @Test
    void rejectsConfidenceOutsideInclusiveRangeAndNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> findingWithConfidence(-0.01));
        assertThrows(IllegalArgumentException.class, () -> findingWithConfidence(1.01));
        assertThrows(IllegalArgumentException.class, () -> findingWithConfidence(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> findingWithConfidence(Double.POSITIVE_INFINITY));
    }

    @Test
    void acceptsConfidenceBoundariesAndRejectsInvalidSourceCoordinates() {
        assertEquals(0.0, findingWithConfidence(0.0).confidence());
        assertEquals(1.0, findingWithConfidence(1.0).confidence());
        assertEquals(1, new SourceLocation("plugin.yml", 1, 1).line());
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation("plugin.yml", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation("plugin.yml", 1, 0));
    }

    @Test
    void rejectsBlankRequiredTextAndEvidence() {
        assertThrows(IllegalArgumentException.class, () -> findingWithRuleId(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> finding(List.of("")));
    }

    private static Finding finding(List<String> evidence) {
        return new Finding(
                "rule.example",
                "analyzer.example",
                Severity.MEDIUM,
                "compatibility",
                "Example finding",
                "An example description.",
                evidence,
                Optional.empty(),
                Optional.empty(),
                Optional.of("plugin.jar"),
                Optional.of(new SourceLocation("plugin.yml", 1, 1)),
                Optional.empty(),
                0.75,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static Finding findingWithConfidence(double confidence) {
        Finding baseline = finding(List.of("Evidence"));
        return new Finding(
                baseline.ruleId(),
                baseline.analyzerId(),
                baseline.severity(),
                baseline.category(),
                baseline.title(),
                baseline.description(),
                baseline.evidence(),
                baseline.affectedClass(),
                baseline.affectedMethod(),
                baseline.affectedFile(),
                baseline.sourceLocation(),
                baseline.suggestedFix(),
                confidence,
                baseline.platform(),
                baseline.version(),
                baseline.documentationReference());
    }

    private static Finding findingWithRuleId(String ruleId) {
        Finding baseline = finding(List.of("Evidence"));
        return new Finding(
                ruleId,
                baseline.analyzerId(),
                baseline.severity(),
                baseline.category(),
                baseline.title(),
                baseline.description(),
                baseline.evidence(),
                baseline.affectedClass(),
                baseline.affectedMethod(),
                baseline.affectedFile(),
                baseline.sourceLocation(),
                baseline.suggestedFix(),
                baseline.confidence(),
                baseline.platform(),
                baseline.version(),
                baseline.documentationReference());
    }
}
