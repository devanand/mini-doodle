package com.doodle.scheduler.slot.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A user's calendar: the aggregation of their TimeSlots over a window.
 *
 * <p>Deliberately not persisted - there is no calendars table, and the term
 * never appears in the API (endpoint, DTO, or JSON field). "Calendar
 * should be present only in the domain" is satisfied by keeping this type
 * confined to slot.model and SlotService's return type; AvailabilityResponse
 * unpacks it rather than exposing it directly.
 *
 * <p>Status filtering already happened in SQL (see SlotSpecifications) by
 * the time a Calendar exists, so this type is not a second filtering layer -
 * it is the aggregate the domain reasons about, with behavior a raw
 * List<TimeSlot> would not carry on its own.
 */
public record Calendar(UUID ownerId, Instant from, Instant to, List<TimeSlot> slots) {

    public List<TimeSlot> freeSlots() {
        return slots.stream().filter(TimeSlot::isFree).toList();
    }

    public List<TimeSlot> busySlots() {
        return slots.stream().filter(slot -> !slot.isFree()).toList();
    }

    public boolean isFullyBooked() {
        return !slots.isEmpty() && slots.stream().noneMatch(TimeSlot::isFree);
    }

    public Duration totalFreeTime() {
        return freeSlots().stream()
                .map(slot -> Duration.between(slot.getStartTime(), slot.getEndTime()))
                .reduce(Duration.ZERO, Duration::plus);
    }
}