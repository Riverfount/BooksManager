package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A small in-memory cache with a time to live and a size limit, so that the same ISBN is not asked again and again
 * in a row (RF06). The oldest entry goes when it is full. Synchronized: the lookup is rare and short, so the lock
 * costs nothing that matters.
 */
final class TtlCache<K, V> {

    private record Held<V>(V value, Instant expiresAt) {
    }

    private final Clock clock;
    private final Duration ttl;
    private final Map<K, Held<V>> entries;

    TtlCache(Clock clock, Duration ttl, int maxSize) {
        this.clock = clock;
        this.ttl = ttl;
        this.entries = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Held<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    /** The value kept for the key, or empty when there is none or it expired. */
    synchronized Optional<V> get(K key) {
        var entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    synchronized void put(K key, V value) {
        entries.remove(key);
        entries.put(key, new Held<>(value, clock.instant().plus(ttl)));
    }
}
