package com.doodle.scheduler.slot.rule.check;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.slot.rule.ModifySlotRule;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Only relevant when modifying an existing slot - a brand new slot has no prior state to conflict with. */
@Component
@Order(5)
public class NotAlreadyBookedRule implements ModifySlotRule {

    @Override
    public void validate(SlotCandidate candidate) {
        if (candidate.existing() != null && !candidate.existing().isFree()) {
            throw new ConflictException("Cannot modify a slot that is already booked - cancel the meeting first");
        }
    }
}
