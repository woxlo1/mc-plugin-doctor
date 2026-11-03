package io.github.woxlo1.plugindoctor.core.rules;

import io.github.woxlo1.plugindoctor.core.model.Finding;

public interface AnalysisRule {
    String id();

    RuleDecision evaluate(Finding finding);
}
