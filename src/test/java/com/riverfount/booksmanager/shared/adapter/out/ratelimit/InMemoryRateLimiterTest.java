package com.riverfount.booksmanager.shared.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.shared.application.port.out.RateLimitPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * The limit of attempts in memory (Phase 6): a sliding window per policy and key, with the clock injected so no test
 * sleeps. The window moves with the attempts, not with the wall clock, so there is no burst at the turn of a window.
 */
class InMemoryRateLimiterTest {

    private static final Instant START = Instant.parse("2026-10-27T12:00:00Z");
    private static final RateLimitPolicy THREE_PER_HOUR = new RateLimitPolicy("reset", 3, Duration.ofHours(1));

    /** A clock the tests move forward by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = START;

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    void shouldAllowTheAttemptsUpToTheLimitAndRefuseTheNext() {
        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "ana").allowed()).isTrue();
        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "ana").allowed()).isTrue();
        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "ana").allowed()).isTrue();

        var refused = limiter.tryAcquire(THREE_PER_HOUR, "ana");

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfter()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void shouldSayWhenTheOldestAttemptLeavesTheWindow() {
        limiter.tryAcquire(THREE_PER_HOUR, "ana");
        clock.advance(Duration.ofMinutes(20));
        limiter.tryAcquire(THREE_PER_HOUR, "ana");
        limiter.tryAcquire(THREE_PER_HOUR, "ana");
        clock.advance(Duration.ofMinutes(10));

        var refused = limiter.tryAcquire(THREE_PER_HOUR, "ana");

        // the first attempt was 30 minutes ago, so a place is free in 30 more
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfter()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void shouldAllowAgainWhenTheWindowHasPassed() {
        for (var i = 0; i < 3; i++) {
            limiter.tryAcquire(THREE_PER_HOUR, "ana");
        }
        clock.advance(Duration.ofHours(1));

        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "ana").allowed()).isTrue();
    }

    @Test
    void aRefusedAttemptShouldNotExtendTheBlock() {
        for (var i = 0; i < 3; i++) {
            limiter.tryAcquire(THREE_PER_HOUR, "ana");
        }
        clock.advance(Duration.ofMinutes(59));
        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "ana").allowed()).isFalse();
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "ana").allowed()).isTrue();
    }

    @Test
    void shouldCountEachKeyAndEachPolicySeparately() {
        var other = new RateLimitPolicy("other", 3, Duration.ofHours(1));
        for (var i = 0; i < 3; i++) {
            limiter.tryAcquire(THREE_PER_HOUR, "ana");
        }

        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "ana").allowed()).isFalse();
        assertThat(limiter.tryAcquire(THREE_PER_HOUR, "bia").allowed()).isTrue();
        assertThat(limiter.tryAcquire(other, "ana").allowed()).isTrue();
    }

    @Test
    void shouldLetExactlyTheLimitThroughWhenManyThreadsTryAtOnce() throws Exception {
        var policy = new RateLimitPolicy("burst", 50, Duration.ofMinutes(1));
        var threads = 8;
        var barrier = new CyclicBarrier(threads);
        var pool = Executors.newFixedThreadPool(threads);
        var allowed = 0;
        try {
            Callable<Integer> attempts = () -> {
                barrier.await();
                var count = 0;
                for (var i = 0; i < 100; i++) {
                    if (limiter.tryAcquire(policy, "same").allowed()) {
                        count++;
                    }
                }
                return count;
            };
            var calls = new ArrayList<Callable<Integer>>();
            for (var i = 0; i < threads; i++) {
                calls.add(attempts);
            }
            for (var result : pool.invokeAll(calls)) {
                allowed += result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed).isEqualTo(50);
    }

    @Test
    void shouldForgetTheKeysWhoseWindowHasPassedSoMemoryDoesNotGrowForever() {
        for (var i = 0; i < 5000; i++) {
            limiter.tryAcquire(THREE_PER_HOUR, "key-" + i);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(5000);
        clock.advance(Duration.ofHours(2));

        // the sweep runs along with the attempts; enough of them make sure it ran
        for (var i = 0; i < 2000; i++) {
            limiter.tryAcquire(THREE_PER_HOUR, "fresh");
        }

        assertThat(limiter.trackedKeys()).isLessThan(10);
    }

    @Test
    void shouldRefuseNewKeysRatherThanGrowWithoutLimitWhenTheyAreFlooded() {
        var small = new InMemoryRateLimiter(clock, 100);
        for (var i = 0; i < 100; i++) {
            assertThat(small.tryAcquire(THREE_PER_HOUR, "key-" + i).allowed()).isTrue();
        }

        // every key is still inside its window, so there is nothing to forget: a new one is refused (fail closed)
        assertThat(small.tryAcquire(THREE_PER_HOUR, "one-more").allowed()).isFalse();
        // a key already known keeps being counted by its own limit
        assertThat(small.tryAcquire(THREE_PER_HOUR, "key-1").allowed()).isTrue();
        assertThat(small.trackedKeys()).isEqualTo(100);
    }
}
