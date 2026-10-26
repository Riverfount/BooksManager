package com.riverfount.booksmanager.shared.application.port.out;

import java.time.Duration;

/**
 * The answer of the limiter: whether the attempt may go on and, when it may not, how long until a place is free.
 * An allowed attempt has no wait.
 */
public record RateLimitResult(boolean allowed, Duration retryAfter) {

    public static RateLimitResult allow() {
        return new RateLimitResult(true, Duration.ZERO);
    }

    public static RateLimitResult refuse(Duration retryAfter) {
        return new RateLimitResult(false, retryAfter);
    }
}
