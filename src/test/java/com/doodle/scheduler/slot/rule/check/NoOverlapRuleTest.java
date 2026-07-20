package com.doodle.scheduler.slot.rule.check;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.slot.rule.OverlapChecker;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The rule itself only delegates to OverlapChecker and translates the
 * result - the actual overlap arithmetic lives in SlotSpecifications and is
 * covered end-to-end by the smoke/race scripts against a real database,
 * not here.
 */
@ExtendWith(MockitoExtension.class)
class NoOverlapRuleTest {

    @Mock
    private OverlapChecker overlapChecker;

    @Test
    void passes_whenCheckerReportsNoOverlap() {
        NoOverlapRule rule = new NoOverlapRule(overlapChecker);
        UUID ownerId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        when(overlapChecker.overlaps(ownerId, null, start, end)).thenReturn(false);

        assertThatCode(() -> rule.validate(SlotCandidate.forCreate(ownerId, start, end)))
                .doesNotThrowAnyException();
    }

    @Test
    void throwsConflict_whenCheckerReportsOverlap() {
        NoOverlapRule rule = new NoOverlapRule(overlapChecker);
        UUID ownerId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        when(overlapChecker.overlaps(ownerId, null, start, end)).thenReturn(true);

        assertThatThrownBy(() -> rule.validate(SlotCandidate.forCreate(ownerId, start, end)))
                .isInstanceOf(ConflictException.class);
    }
}