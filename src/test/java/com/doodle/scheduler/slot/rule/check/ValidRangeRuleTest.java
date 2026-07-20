package com.doodle.scheduler.slot.rule.check;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.doodle.scheduler.shared.exception.BadRequestException;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidRangeRuleTest {

    private final ValidRangeRule rule = new ValidRangeRule();

    @Test
    void passes_whenStartIsBeforeEnd() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        SlotCandidate candidate = SlotCandidate.forCreate(UUID.randomUUID(), start, end);

        assertThatCode(() -> rule.validate(candidate)).doesNotThrowAnyException();
    }

    @Test
    void throwsBadRequest_whenStartEqualsEnd() {
        Instant instant = Instant.now();
        SlotCandidate candidate = SlotCandidate.forCreate(UUID.randomUUID(), instant, instant);

        assertThatThrownBy(() -> rule.validate(candidate)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void throwsBadRequest_whenStartIsAfterEnd() {
        Instant start = Instant.now();
        Instant end = start.minusSeconds(3600);
        SlotCandidate candidate = SlotCandidate.forCreate(UUID.randomUUID(), start, end);

        assertThatThrownBy(() -> rule.validate(candidate)).isInstanceOf(BadRequestException.class);
    }
}