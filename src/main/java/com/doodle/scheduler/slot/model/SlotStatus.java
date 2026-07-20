package com.doodle.scheduler.slot.model;

/**
 * A TimeSlot's booking state. FREE and BUSY only - matches the
 * chk_slot_status CHECK constraint in V1__create_initial_schema.sql
 * exactly; adding a value here without a matching migration would let
 * Hibernate write a status Postgres then rejects at insert time.
 */
public enum SlotStatus {
    FREE,
    BUSY
}
