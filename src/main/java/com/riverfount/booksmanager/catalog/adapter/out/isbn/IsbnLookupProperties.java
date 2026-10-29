package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the lookup of books by ISBN (RF06), read from {@code app.isbn-lookup.*}. Off by default (RNF10) and in the
 * tests, so nothing goes out to the network by accident. The timeouts are short on purpose: a source that is slow
 * cannot hold a request thread, and the registration by hand goes on without it (RNF19). BrasilAPI asks for no
 * key, so there is no secret here.
 */
@ConfigurationProperties(prefix = "app.isbn-lookup")
public record IsbnLookupProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://brasilapi.com.br") String baseUrl,
        @DefaultValue("2s") Duration connectionTimeout,
        @DefaultValue("3s") Duration timeout,
        @DefaultValue("10m") Duration cacheTtl,
        @DefaultValue("500") int cacheMaxSize) {

    public IsbnLookupProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("app.isbn-lookup.base-url must not be blank");
        }
        if (cacheMaxSize < 1) {
            throw new IllegalArgumentException("app.isbn-lookup.cache-max-size must be at least 1");
        }
    }
}
