package com.riverfount.booksmanager.shared.adapter.out.persistence;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifies which database constraint was violated in a
 * DataIntegrityViolationException, so that persistence adapters only
 * translate the expected violation (e.g. unique name) into
 * BusinessRuleException, without confusing it with other causes (e.g.
 * an invalid foreign key).
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    public static boolean violates(DataIntegrityViolationException exception, String constraintName) {
        var message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
