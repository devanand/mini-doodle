package com.doodle.scheduler.slot.model;

import java.util.UUID;

/**
 * What SlotBookingPort exposes about a slot once it's been marked busy -
 * deliberately not TimeSlot. Meeting needs the slot's id and owner to
 * build a Meeting; it has no business seeing status, version, or start/end
 * time, and importing TimeSlot itself would pull slot's internal domain
 * model across the bounded-context boundary for no reason.
 */
public record BookedSlot(UUID id, UUID ownerId) {}