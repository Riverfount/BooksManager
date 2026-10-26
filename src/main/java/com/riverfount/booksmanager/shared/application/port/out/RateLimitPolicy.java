package com.riverfount.booksmanager.shared.application.port.out;

import java.time.Duration;

/**
 * How many attempts a key may make inside a sliding window (Phase 6, RNF31): {@code name} tells the policies apart
 * (the same key is counted separately under each one), {@code maxAttempts} is how many are allowed in any
 * {@code window} of time.
 */
public record RateLimitPolicy(String name, int maxAttempts, Duration window) {

    public RateLimitPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("the policy needs a name");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("a policy allows at least one attempt");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("the window of a policy must be positive");
        }
    }
}
