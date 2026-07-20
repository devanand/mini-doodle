package com.doodle.scheduler.slot.dto;

import com.doodle.scheduler.slot.model.Calendar;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(UUID ownerId, Instant from, Instant to, List<SlotResponse> slots) {

    // Unpacks the domain Calendar rather than exposing it: the API contract
    // stays "availability", the word "calendar" never reaches a JSON field
    // or an endpoint name.
    public static AvailabilityResponse from(Calendar calendar) {
        return new AvailabilityResponse(
                calendar.ownerId(),
                calendar.from(),
                calendar.to(),
                calendar.slots().stream().map(SlotResponse::from).toList());
    }
}