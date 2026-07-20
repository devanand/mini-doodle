package com.doodle.scheduler.slot.rule;

import com.doodle.scheduler.slot.model.TimeSlot;
import java.time.Instant;
import java.util.UUID;

/**
 * The state a slot is being validated against - either a brand new slot
 * being created, or an existing slot being changed to a new time range.
 */
public record SlotCandidate(
        UUID ownerId, UUID excludeSlotId, Instant startTime, Instant endTime, TimeSlot existing) {

    public static SlotCandidate forCreate(UUID ownerId, Instant startTime, Instant endTime) {
        return new SlotCandidate(ownerId, null, startTime, endTime, null);
    }

    public static SlotCandidate forModify(TimeSlot existing, Instant newStartTime, Instant newEndTime) {
        return new SlotCandidate(existing.getOwnerId(), existing.getId(), newStartTime, newEndTime, existing);
    }
}
