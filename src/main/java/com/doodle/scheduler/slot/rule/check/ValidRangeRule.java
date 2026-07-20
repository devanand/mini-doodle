package com.doodle.scheduler.slot.rule.check;

import com.doodle.scheduler.shared.exception.BadRequestException;
import com.doodle.scheduler.slot.rule.CreateSlotRule;
import com.doodle.scheduler.slot.rule.ModifySlotRule;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)

public class ValidRangeRule implements CreateSlotRule, ModifySlotRule {

    @Override
    public void validate(SlotCandidate candidate) {
        if (!candidate.startTime().isBefore(candidate.endTime())) {
            throw new BadRequestException("Slot start time must be before its end time");
        }
    }
}
