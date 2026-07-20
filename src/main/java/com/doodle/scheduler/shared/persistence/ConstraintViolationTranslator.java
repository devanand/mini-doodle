package com.doodle.scheduler.shared.persistence;

import com.doodle.scheduler.shared.exception.ConflictException;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Translates one specific, known database constraint violation into a
 * ConflictException, without swallowing every DataIntegrityViolationException
 * as if it were that violation. Used at every JPA-touching seam that guards
 * an invariant a DB constraint enforces but a Java-side check can only race
 * against - a read-then-write check (NoOverlapRule, the meetings.slot_id
 * uniqueness assumption) can pass for two concurrent requests; only the
 * constraint itself is authoritative, and only saveAndFlush (not save)
 * surfaces its violation before the caller returns.
 *
 * <p>Anything that isn't the named constraint is rethrown unchanged - a
 * violation of some other constraint is a real bug, not the expected race,
 * and should surface as a 500, not get mislabeled as this conflict.
 */
public final class ConstraintViolationTranslator {

    private ConstraintViolationTranslator() {}

    public static <T> T guard(Supplier<T> operation, String constraintName, String conflictMessage) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException ex) {
            if (isViolationOf(ex, constraintName)) {
                throw new ConflictException(conflictMessage);
            }
            throw ex;
        }
    }

    private static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        return ex.getCause() instanceof ConstraintViolationException cve
                && constraintName.equals(cve.getConstraintName());
    }
}