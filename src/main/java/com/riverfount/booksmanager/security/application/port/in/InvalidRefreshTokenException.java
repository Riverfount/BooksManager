package com.riverfount.booksmanager.security.application.port.in;

/**
 * Signals a refresh token that cannot be used (RF08): unknown, missing,
 * expired, already revoked or of a user who is no longer active. The message is
 * always the same, so nothing tells which of these it was. The web adapter
 * answers 401 and clears the cookie.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public static final String MESSAGE = "refresh token inválido, expirado ou revogado";

    public InvalidRefreshTokenException() {
        super(MESSAGE);
    }
}
