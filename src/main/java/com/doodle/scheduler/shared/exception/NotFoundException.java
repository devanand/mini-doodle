package com.doodle.scheduler.shared.exception;

/** Thrown when a requested entity (user, slot, meeting) does not exist. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
