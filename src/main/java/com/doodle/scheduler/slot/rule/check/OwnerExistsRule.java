package com.doodle.scheduler.slot.rule.check;

import com.doodle.scheduler.shared.exception.NotFoundException;
import com.doodle.scheduler.slot.rule.CreateSlotRule;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import com.doodle.scheduler.user.UserExistenceChecker;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class OwnerExistsRule implements CreateSlotRule {

    private final UserExistenceChecker userExistenceChecker;

    OwnerExistsRule(UserExistenceChecker userExistenceChecker) {
        this.userExistenceChecker = userExistenceChecker;
    }

    @Override
    public void validate(SlotCandidate candidate) {
        if (!userExistenceChecker.exists(candidate.ownerId())) {
            throw new NotFoundException("No user found with id '%s'".formatted(candidate.ownerId()));
        }
    }
}
