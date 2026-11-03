package io.github.woxlo1.plugindoctor.core.rules;

import io.github.woxlo1.plugindoctor.core.model.Severity;
import java.util.Objects;
import java.util.Optional;

public record RuleDecision(
        boolean suppressed,
        Optional<Severity> severityOverride,
        String rationale) {
    public RuleDecision {
        severityOverride = Objects.requireNonNull(severityOverride, "severityOverride");
        rationale = Objects.requireNonNull(rationale, "rationale");
        if (suppressed && severityOverride.isPresent()) {
            throw new IllegalArgumentException("a suppressed finding cannot have a severity override");
        }
        if ((suppressed || severityOverride.isPresent()) && rationale.isBlank()) {
            throw new IllegalArgumentException("a rule change must include a rationale");
        }
    }

    public static RuleDecision keep() {
        return new RuleDecision(false, Optional.empty(), "");
    }

    public static RuleDecision suppress(String rationale) {
        return new RuleDecision(true, Optional.empty(), rationale);
    }

    public static RuleDecision overrideSeverity(Severity severity, String rationale) {
        return new RuleDecision(false, Optional.of(severity), rationale);
    }

    public boolean changedFinding() {
        return suppressed || severityOverride.isPresent();
    }
}
