package com.riverfount.booksmanager.shared.domain;

/**
 * Signals that a resource looked up by identifier does not exist (or no
 * longer exists). Mapped to status 404 by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
