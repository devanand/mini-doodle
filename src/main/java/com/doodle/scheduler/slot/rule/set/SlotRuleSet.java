package com.doodle.scheduler.slot.rule.set;

import com.doodle.scheduler.slot.rule.SlotCandidate;
import com.doodle.scheduler.slot.rule.SlotRule;

import java.util.List;

/**
 * A composite of SlotRules applied as one unit. Callers see a single
 * "validate this candidate" operation and never handle the rule
 * collection themselves.
 */
public abstract class SlotRuleSet {

    private final List<? extends SlotRule> rules;

    protected SlotRuleSet(List<? extends SlotRule> rules) {
        this.rules = rules;
    }

    public void apply(SlotCandidate candidate) {
        rules.forEach(rule -> rule.validate(candidate));
    }
}
