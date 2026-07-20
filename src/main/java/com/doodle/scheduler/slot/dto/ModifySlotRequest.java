package com.doodle.scheduler.slot.dto;

import com.doodle.scheduler.slot.command.ModifySlotCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public record ModifySlotRequest(@NotNull Instant startTime, @Positive long durationMinutes) {
    public ModifySlotCommand toCommand(UUID slotId) {
        return new ModifySlotCommand(slotId, startTime, durationMinutes);
    }
}
