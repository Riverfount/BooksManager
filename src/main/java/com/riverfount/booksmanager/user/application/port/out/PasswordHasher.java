package com.riverfount.booksmanager.user.application.port.out;

/**
 * Outbound port that turns a password into the hash that is stored (RNF07).
 * The domain does not know the algorithm; the adapter uses BCrypt.
 */
public interface PasswordHasher {

    /** Returns a salted hash of the password; the password itself is never returned. */
    String hash(String rawPassword);

    /**
     * Whether the password is the one that produced the stored hash. A missing
     * or malformed hash, or a missing password, simply does not match; it
     * never throws.
     */
    boolean matches(String rawPassword, String storedHash);
}
