package com.doodle.scheduler.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import com.doodle.scheduler.slot.model.TimeSlot_;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

/**
 * SlotSpecifications cannot be fully verified without a real database - the
 * question "does this predicate select the right rows" is answered by
 * scripts/smoke-test.sh and scripts/race-test.sh against Postgres, not
 * here (Testcontainers was deliberately not added - see README).
 *
 * <p>What this class DOES cover, with no database at all:
 *
 * <ol>
 *   <li>Specification.and() composition never throws when optional filters
 *       are absent. This is the direct regression test for the bug this
 *       project actually hit: Spring Data JPA 4.x's and() rejects a null
 *       Specification, and hasStatus()/excludingId() originally returned
 *       null for "no filter" before being rewritten to return
 *       cb.conjunction() instead. Composition needs no Root or
 *       CriteriaBuilder - toPredicate() is never invoked here.
 *   <li>hasStatus() and excludingId() select the correct branch
 *       (conjunction vs a real predicate) for a given input, verified with
 *       a mocked CriteriaBuilder.
 * </ol>
 *
 * <p>ownedBy(), startsWithin(), and overlapping() are not mocked here:
 * verifying their exact Root.get(...)/CriteriaBuilder call chains correctly
 * without a real build to compile-check against risks brittle test code
 * that looks right but is not. Their correctness is covered end-to-end by
 * the smoke and race test scripts instead.
 */
@ExtendWith(MockitoExtension.class)
class SlotSpecificationsTest {

    @Test
    void composesWithoutThrowing_whenAllOptionalFiltersAreAbsent() {
        UUID ownerId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);

        // The exact shape SlotOverlapChecker builds for a create (no
        // exclusion) and SlotService.getAvailability builds (no status
        // filter). Before the fix, either null-returning factory made this
        // throw IllegalArgumentException at composition time, before a
        // query ever ran.
        assertThatCode(() -> {
            Specification<TimeSlot> forCreate = SlotSpecifications.ownedBy(ownerId)
                    .and(SlotSpecifications.overlapping(start, end))
                    .and(SlotSpecifications.excludingId(null));

            Specification<TimeSlot> forAvailability = SlotSpecifications.ownedBy(ownerId)
                    .and(SlotSpecifications.startsWithin(start, end))
                    .and(SlotSpecifications.hasStatus(null));
        })
                .doesNotThrowAnyException();
    }

    @Test
    void hasStatus_producesAlwaysTruePredicate_whenStatusIsNull(
            @Mock Root<TimeSlot> root, @Mock CriteriaQuery<?> query, @Mock CriteriaBuilder cb, @Mock Predicate always) {
        when(cb.conjunction()).thenReturn(always);

        Predicate result = SlotSpecifications.hasStatus(null).toPredicate(root, query, cb);

        verify(cb).conjunction();
        verify(cb, never()).equal(any(), any());
        assertThat(result).isSameAs(always);
    }

    @Test
    void hasStatus_producesEqualityPredicate_whenStatusIsProvided(
            @Mock Root<TimeSlot> root,
            @Mock CriteriaQuery<?> query,
            @Mock CriteriaBuilder cb,
            @Mock Path<SlotStatus> statusPath,
            @Mock Predicate equalityPredicate) {
        when(root.get(TimeSlot_.status)).thenReturn(statusPath);
        when(cb.equal(statusPath, SlotStatus.FREE)).thenReturn(equalityPredicate);

        Predicate result = SlotSpecifications.hasStatus(SlotStatus.FREE).toPredicate(root, query, cb);

        verify(cb, never()).conjunction();
        assertThat(result).isSameAs(equalityPredicate);
    }

    @Test
    void excludingId_producesAlwaysTruePredicate_whenIdIsNull(
            @Mock Root<TimeSlot> root, @Mock CriteriaQuery<?> query, @Mock CriteriaBuilder cb, @Mock Predicate always) {
        when(cb.conjunction()).thenReturn(always);

        Predicate result = SlotSpecifications.excludingId(null).toPredicate(root, query, cb);

        verify(cb).conjunction();
        verify(cb, never()).notEqual(any(), any());
        assertThat(result).isSameAs(always);
    }

    @Test
    void excludingId_producesNotEqualPredicate_whenIdIsProvided(
            @Mock Root<TimeSlot> root,
            @Mock CriteriaQuery<?> query,
            @Mock CriteriaBuilder cb,
            @Mock Path<UUID> idPath,
            @Mock Predicate notEqualPredicate) {
        UUID excludedId = UUID.randomUUID();
        when(root.get(TimeSlot_.id)).thenReturn(idPath);
        when(cb.notEqual(idPath, excludedId)).thenReturn(notEqualPredicate);

        Predicate result = SlotSpecifications.excludingId(excludedId).toPredicate(root, query, cb);

        verify(cb, never()).conjunction();
        assertThat(result).isSameAs(notEqualPredicate);
    }
}