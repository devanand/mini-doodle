package com.doodle.scheduler.slot.command;

import com.doodle.scheduler.slot.model.TimeSlot;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Service-layer input for slot modification. Unlike CreateSlotCommand this
 * has no owner - the target slot's owner is not client-supplied and
 * cannot change (see OwnerExistsRule, which is create-only for the same
 * reason).
 */
public record ModifySlotCommand(UUID slotId, Instant startTime, long durationMinutes) {

    // public for the same reason as CreateSlotCommand.endTime()/toCandidate().
    public Instant endTime() {
        return startTime.plus(Duration.ofMinutes(durationMinutes));
    }

    public SlotCandidate toCandidate(TimeSlot existing) {
        return SlotCandidate.forModify(existing, startTime, endTime());
    }

    /**
     * Applies this command's new time range onto an already-persisted slot.
     * Not a mapping to a new instance, unlike CreateSlotCommand.toTimeSlot():
     * modification is a mutation of an existing row, not a construction, so
     * there is no "return type" symmetry to force here - the command still
     * owns translating itself onto the domain type either way.
     */
    public void applyTo(TimeSlot existing) {
        existing.setStartTime(startTime);
        existing.setEndTime(endTime());
    }
}