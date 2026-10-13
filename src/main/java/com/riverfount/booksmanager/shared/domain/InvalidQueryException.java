package com.riverfount.booksmanager.shared.domain;

/**
 * Signals invalid query parameters, such as an out-of-range page size or
 * an unknown sort field. Mapped to status 400 by GlobalExceptionHandler.
 */
public class InvalidQueryException extends RuntimeException {

    public InvalidQueryException(String message) {
        super(message);
    }
}
