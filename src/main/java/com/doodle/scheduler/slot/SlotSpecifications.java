package com.doodle.scheduler.slot;

import java.time.Instant;
import java.util.UUID;
import com.doodle.scheduler.slot.model.TimeSlot_;

import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable predicates over TimeSlot.
 *
 * <p>Each factory returns one predicate; callers combine them with and().
 * Optional filters return an always-true predicate (cb.conjunction(), i.e.
 * 1=1, which the planner discards) rather than null: Spring Data JPA 4.x
 * rejects null in Specification.and(). That is what removes the
 * (:status is null or ...) idiom the JPQL versions needed, and lets one
 * expression serve both the create and modify overlap checks.
 *
 * <p>Attributes are referenced through the generated static metamodel
 * (TimeSlot_) rather than string literals, so a renamed or removed field
 * fails compilation. String-based Specifications would only fail when the
 * query executed - strictly worse than the @Query JPQL this replaced,
 * which Spring Data validates at startup.
 */
final class SlotSpecifications {

    private SlotSpecifications() {}

    static Specification<TimeSlot> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get(TimeSlot_.ownerId), ownerId);
    }

    /** Slots whose start falls in [from, to). */
    static Specification<TimeSlot> startsWithin(Instant from, Instant to) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get(TimeSlot_.startTime), from),
                cb.lessThan(root.get(TimeSlot_.startTime), to));
    }

    /** Optional filter: a null status means "any status" and constrains nothing. */
    static Specification<TimeSlot> hasStatus(SlotStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get(TimeSlot_.status), status);
    }

    /**
     * Half-open interval overlap: [start, end) intersects [startTime, endTime).
     * Strict comparisons on both sides, so slots that merely touch at a
     * boundary (10:00-11:00 and 11:00-12:00) do not count as overlapping.
     */
    static Specification<TimeSlot> overlapping(Instant start, Instant end) {
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get(TimeSlot_.startTime), end), cb.greaterThan(root.get(TimeSlot_.endTime), start));
    }

    /**
     * Optional exclusion: a null id excludes nothing. Needed when modifying
     * a slot, which always overlaps its own current range; on create there
     * is nothing to exclude.
     */
    static Specification<TimeSlot> excludingId(UUID slotId) {
        return (root, query, cb) ->
                slotId == null ? cb.conjunction() : cb.notEqual(root.get(TimeSlot_.id), slotId);
    }
}