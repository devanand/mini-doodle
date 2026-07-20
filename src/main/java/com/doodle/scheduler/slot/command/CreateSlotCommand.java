package com.doodle.scheduler.slot.command;

import com.doodle.scheduler.slot.model.TimeSlot;
import com.doodle.scheduler.slot.rule.SlotCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Service-layer input for slot creation. Duration is the API-level concept
 * (see CreateSlotRequest); endTime is derived here once, so every caller
 * downstream - the rule chain and the entity itself - works from an
 * already-resolved end time rather than recomputing it.
 */
public record CreateSlotCommand(UUID ownerId, Instant startTime, long durationMinutes) {

    // public, not package-private: this command now lives in a different
    // package (slot.command) than its only caller, SlotService (slot).
    // Package-private visibility could hide this from other consumers when
    // everything shared one package; it cannot express "only SlotService"
    // once they are in different packages - that constraint is now just a
    // convention, not compiler-enforced.
    public Instant endTime() {
        return startTime.plus(Duration.ofMinutes(durationMinutes));
    }

    public SlotCandidate toCandidate() {
        return SlotCandidate.forCreate(ownerId, startTime, endTime());
    }

    /**
     * Maps inward to the domain type, same direction as
     * CreateSlotRequest.toCommand(): each layer knows how to translate
     * itself into the next one in, never the reverse. SlotService never
     * touches TimeSlot.builder() directly.
     */
    public TimeSlot toTimeSlot() {
        return TimeSlot.builder().ownerId(ownerId).startTime(startTime).endTime(endTime()).build();
    }
}