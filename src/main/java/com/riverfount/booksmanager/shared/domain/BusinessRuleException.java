package com.riverfount.booksmanager.shared.domain;

/**
 * Signals the violation of a domain business rule (for example, invalid
 * data or a disallowed state transition).
 *
 * <p>Use this exception in domain entities and policies, never in
 * adapters. Later, the web adapter converts this exception into a
 * {@code ProblemDetail} (RFC 9457), with status 422, in the API response
 * (RNF04).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
