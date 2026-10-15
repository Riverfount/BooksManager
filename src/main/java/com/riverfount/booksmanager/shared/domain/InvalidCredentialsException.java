package com.riverfount.booksmanager.shared.domain;

/**
 * Signals a failed login (RF08): the credentials do not match an active
 * user. The message is always the same, so nothing tells whether the login
 * does not exist, the password is wrong or the user is inactive. The web
 * adapter converts it into a 401 ProblemDetail.
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "login ou senha inválidos";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
