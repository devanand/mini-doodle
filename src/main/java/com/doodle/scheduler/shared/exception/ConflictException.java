package com.doodle.scheduler.shared.exception;

/**
 * Thrown when a request conflicts with the current state of a resource
 * (duplicate email, overlapping slot, already-booked slot, concurrent
 * booking race). Maps to HTTP 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
