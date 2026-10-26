package com.riverfount.booksmanager.shared.application.port.out;

/**
 * Outbound port that limits the attempts of a key under a policy (Phase 6, RNF31): the requests of a reader, the
 * password reset requests of an e-mail. Every call counts one attempt, and a refused attempt does not extend the wait.
 * The key is an opaque text the caller builds from what it limits; it should not be a secret.
 */
public interface RateLimiter {

    /** Counts one attempt of the key under the policy and says whether it may go on. */
    RateLimitResult tryAcquire(RateLimitPolicy policy, String key);
}
