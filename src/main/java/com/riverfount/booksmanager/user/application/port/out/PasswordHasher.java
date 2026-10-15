package com.riverfount.booksmanager.user.application.port.out;

/**
 * Outbound port that turns a password into the hash that is stored (RNF07).
 * The domain does not know the algorithm; the adapter uses BCrypt.
 */
public interface PasswordHasher {

    /** Returns a salted hash of the password; the password itself is never returned. */
    String hash(String rawPassword);
}
