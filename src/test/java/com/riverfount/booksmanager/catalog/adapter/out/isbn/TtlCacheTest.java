package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The small cache of the ISBN lookup (RF06): time to live and size limit. */
class TtlCacheTest {

    private Instant now = Instant.parse("2026-11-10T12:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    @Test
    void shouldKeepAValueUntilItsTimeToLiveEnds() {
        var cache = new TtlCache<String, String>(clock, Duration.ofMinutes(5), 10);
        cache.put("a", "1");

        now = now.plus(Duration.ofMinutes(5)).minusSeconds(1);
        assertThat(cache.get("a")).contains("1");

        now = now.plusSeconds(1);
        assertThat(cache.get("a")).isEmpty();
    }

    @Test
    void shouldForgetTheOldestWhenFull() {
        var cache = new TtlCache<String, String>(clock, Duration.ofMinutes(5), 2);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).contains("2");
        assertThat(cache.get("c")).contains("3");
    }

    @Test
    void shouldRenewTheTimeToLiveWhenAValueIsPutAgain() {
        var cache = new TtlCache<String, String>(clock, Duration.ofMinutes(5), 10);
        cache.put("a", "1");
        now = now.plus(Duration.ofMinutes(4));
        cache.put("a", "2");

        now = now.plus(Duration.ofMinutes(4));

        assertThat(cache.get("a")).contains("2");
    }

    @Test
    void shouldAnswerEmptyForAnUnknownKey() {
        assertThat(new TtlCache<String, String>(clock, Duration.ofMinutes(5), 10).get("x")).isEmpty();
    }
}
