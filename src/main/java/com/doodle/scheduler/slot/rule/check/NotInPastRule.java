package com.doodle.scheduler.slot.rule.check;

import com.doodle.scheduler.shared.exception.BadRequestException;
import com.doodle.scheduler.slot.rule.CreateSlotRule;
import com.doodle.scheduler.slot.rule.ModifySlotRule;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(10)
public class NotInPastRule implements CreateSlotRule, ModifySlotRule {

    @Override
    public void validate(SlotCandidate candidate) {
        if (candidate.startTime().isBefore(Instant.now())) {
            throw new BadRequestException("Slot start time cannot be in the past");
        }
    }
}
