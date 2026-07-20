package com.doodle.scheduler.slot.rule;

/**
 * A single slot-validation business rule. Implementations throw on
 * violation and do nothing on success.
 *
 * <p>Open/closed in practice: adding a new rule means adding a new class
 * implementing this interface and listing it in SlotService's rule set -
 * no existing rule implementation is ever touched to add another one.
 */
@FunctionalInterface
public interface SlotRule {
    void validate(SlotCandidate candidate);
}
