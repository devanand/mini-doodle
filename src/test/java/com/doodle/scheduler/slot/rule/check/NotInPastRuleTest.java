package com.doodle.scheduler.slot.rule.check;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.doodle.scheduler.shared.exception.BadRequestException;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The rule reads Instant.now() directly rather than an injected Clock, so
 * an exact-boundary test ("now" vs "one millisecond ago") would be flaky -
 * real time passes between constructing the candidate here and the rule
 * evaluating it. Testing clearly-future and clearly-past values instead
 * avoids that without changing production code just for testability.
 */
class NotInPastRuleTest {

    private final NotInPastRule rule = new NotInPastRule();

    @Test
    void passes_whenStartIsClearlyInTheFuture() {
        Instant future = Instant.now().plus(Duration.ofDays(1));
        SlotCandidate candidate = SlotCandidate.forCreate(UUID.randomUUID(), future, future.plusSeconds(3600));

        assertThatCode(() -> rule.validate(candidate)).doesNotThrowAnyException();
    }

    @Test
    void throwsBadRequest_whenStartIsClearlyInThePast() {
        Instant past = Instant.now().minus(Duration.ofDays(1));
        SlotCandidate candidate = SlotCandidate.forCreate(UUID.randomUUID(), past, past.plusSeconds(3600));

        assertThatThrownBy(() -> rule.validate(candidate)).isInstanceOf(BadRequestException.class);
    }
}