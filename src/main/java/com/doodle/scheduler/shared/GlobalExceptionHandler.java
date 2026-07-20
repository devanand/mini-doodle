package com.doodle.scheduler.shared;

import com.doodle.scheduler.shared.exception.BadRequestException;
import com.doodle.scheduler.shared.exception.ConflictException;
import com.doodle.scheduler.shared.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central mapping from domain/business exceptions to HTTP responses, using
 * RFC 7807 Problem Details (Spring's built-in ProblemDetail type). Keeps
 * HTTP status-code decisions out of the service layer entirely - services
 * just throw plain exceptions and don't know about HTTP at all.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Thrown when @Version-based optimistic locking detects a concurrent
    // update - e.g. two requests both trying to book the same slot. This is
    // a conflict, not a server error, so it maps to 409 like the others.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "This resource was just modified by another request. Please retry.");
    }

    // Fallback for constraint violations a service did not anticipate and
    // translate itself (unique, foreign key, check). Deliberately vague: the
    // constraint name is a database detail and should not leak to clients.
    // Services that know which constraint can fire should catch
    // DataIntegrityViolationException themselves and throw a specific
    // ConflictException with a useful message.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data or references something that does not exist");
    }

    // Bean Validation failures (@NotBlank, @Email, etc. on request DTOs).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("fieldErrors", fieldErrors);

        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
