package com.doodle.scheduler.slot.rule.check;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotAlreadyBookedRuleTest {

    private final NotAlreadyBookedRule rule = new NotAlreadyBookedRule();

    @Test
    void passes_onCreate_whereThereIsNoExistingSlot() {
        SlotCandidate candidate = SlotCandidate.forCreate(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600));

        assertThatCode(() -> rule.validate(candidate)).doesNotThrowAnyException();
    }

    @Test
    void passes_onModify_whenExistingSlotIsFree() {
        TimeSlot free = TimeSlot.builder().status(SlotStatus.FREE).build();
        SlotCandidate candidate = SlotCandidate.forModify(free, Instant.now(), Instant.now().plusSeconds(3600));

        assertThatCode(() -> rule.validate(candidate)).doesNotThrowAnyException();
    }

    @Test
    void throwsConflict_onModify_whenExistingSlotIsAlreadyBooked() {
        TimeSlot busy = TimeSlot.builder().status(SlotStatus.BUSY).build();
        SlotCandidate candidate = SlotCandidate.forModify(busy, Instant.now(), Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> rule.validate(candidate)).isInstanceOf(ConflictException.class);
    }
}