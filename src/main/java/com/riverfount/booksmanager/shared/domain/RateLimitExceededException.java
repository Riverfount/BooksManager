package com.riverfount.booksmanager.shared.domain;

import java.time.Duration;

/**
 * A caller went over the limit of attempts (Phase 6, RNF31). It is answered with 429 and the time to wait, and says
 * nothing about what was counted or for whom.
 */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("too many requests", null, false, false);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
