package com.doodle.scheduler.shared.exception;

/**
 * Thrown for requests that are well-formed but violate a business rule
 * (e.g. modifying a slot that's already in the past). Maps to HTTP 400.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
