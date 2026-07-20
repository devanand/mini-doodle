package com.doodle.scheduler.slot.rule.check;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.slot.rule.CreateSlotRule;
import com.doodle.scheduler.slot.rule.ModifySlotRule;
import com.doodle.scheduler.slot.rule.OverlapChecker;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class NoOverlapRule implements CreateSlotRule, ModifySlotRule {

    private final OverlapChecker overlapChecker;

    public NoOverlapRule(OverlapChecker overlapChecker) {
        this.overlapChecker = overlapChecker;
    }

    @Override
    public void validate(SlotCandidate candidate) {
        boolean overlaps = overlapChecker.overlaps(
                candidate.ownerId(), candidate.excludeSlotId(), candidate.startTime(), candidate.endTime());

        if (overlaps) {
            throw new ConflictException("This time range overlaps an existing slot for this user");
        }
    }
}
