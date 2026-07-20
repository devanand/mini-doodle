package com.doodle.scheduler.slot.dto;

import com.doodle.scheduler.slot.command.CreateSlotCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public record CreateSlotRequest(@NotNull Instant startTime, @Positive long durationMinutes) {
    public CreateSlotCommand toCommand(UUID ownerId) {
        return new CreateSlotCommand(ownerId, startTime, durationMinutes);
    }
}
