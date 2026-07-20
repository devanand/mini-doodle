package com.doodle.scheduler.slot.dto;

import com.doodle.scheduler.slot.model.SlotStatus;
import com.doodle.scheduler.slot.model.TimeSlot;
import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
        UUID id, UUID ownerId, Instant startTime, Instant endTime, SlotStatus status, Instant createdAt) {

    public static SlotResponse from(TimeSlot slot) {
        return new SlotResponse(
                slot.getId(),
                slot.getOwnerId(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getStatus(),
                slot.getCreatedAt());
    }
}
