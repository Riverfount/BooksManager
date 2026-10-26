package com.riverfount.booksmanager.shared.adapter.out.ratelimit;

import com.riverfount.booksmanager.shared.application.port.out.RateLimitPolicy;
import com.riverfount.booksmanager.shared.application.port.out.RateLimitResult;
import com.riverfount.booksmanager.shared.application.port.out.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limits the attempts of a key with a sliding window kept in memory (Phase 6, RNF31). The window moves with the
 * attempts, so there is no burst at the turn of a fixed window, and the clock is injected, so the tests never sleep.
 * Each key is updated atomically, so many threads on the same key let exactly the limit through.
 *
 * <p>It lives in the memory of this instance: the application runs as one (the compose of production has one API), so
 * the count is complete. With several instances each would count apart and the limit would multiply; the day that
 * happens, the port is where a shared store (the database or Redis) goes in.
 *
 * <p>Memory stays bounded. Keys whose window has passed are forgotten along the way, and the number of keys has a
 * ceiling: when it is full of keys still inside their window, a new key is refused (fail closed) rather than let the
 * map grow without limit under a flood of different keys. Nothing but the instants of the attempts is kept.
 */
@Component
class InMemoryRateLimiter implements RateLimiter {

    static final int DEFAULT_MAX_KEYS = 100_000;
    private static final int SWEEP_EVERY = 1024;

    private static final class Entry {
        final ArrayDeque<Instant> hits = new ArrayDeque<>();
        final Duration window;

        Entry(Duration window) {
            this.window = window;
        }

        void dropAttemptsOutsideTheWindow(Instant now) {
            var limit = now.minus(window);
            while (!hits.isEmpty() && !hits.peekFirst().isAfter(limit)) {
                hits.pollFirst();
            }
        }
    }

    private final Clock clock;
    private final int maxKeys;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    @Autowired
    InMemoryRateLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_KEYS);
    }

    InMemoryRateLimiter(Clock clock, int maxKeys) {
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    @Override
    public RateLimitResult tryAcquire(RateLimitPolicy policy, String key) {
        var now = Instant.now(clock);
        if (calls.incrementAndGet() % SWEEP_EVERY == 0) {
            sweep(now);
        }
        var id = policy.name() + '\u0000' + key;
        if (!entries.containsKey(id) && entries.size() >= maxKeys) {
            sweep(now);
            if (!entries.containsKey(id) && entries.size() >= maxKeys) {
                return RateLimitResult.refuse(policy.window());
            }
        }
        var result = new RateLimitResult[1];
        entries.compute(id, (ignored, existing) -> {
            var entry = existing != null ? existing : new Entry(policy.window());
            entry.dropAttemptsOutsideTheWindow(now);
            if (entry.hits.size() < policy.maxAttempts()) {
                entry.hits.addLast(now);
                result[0] = RateLimitResult.allow();
            } else {
                result[0] = RateLimitResult.refuse(Duration.between(now, entry.hits.peekFirst().plus(entry.window)));
            }
            return entry;
        });
        return result[0];
    }

    /** Forgets the keys that have no attempt left inside their window. */
    private void sweep(Instant now) {
        for (var id : entries.keySet()) {
            entries.computeIfPresent(id, (ignored, entry) -> {
                entry.dropAttemptsOutsideTheWindow(now);
                return entry.hits.isEmpty() ? null : entry;
            });
        }
    }

    int trackedKeys() {
        return entries.size();
    }
}
