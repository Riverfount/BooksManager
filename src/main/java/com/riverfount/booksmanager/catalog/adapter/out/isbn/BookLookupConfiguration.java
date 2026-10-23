package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import com.riverfount.booksmanager.catalog.application.port.out.BookLookup;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the {@link BookLookup}: Open Library when {@code app.isbn-lookup.enabled} is true, none otherwise. */
@Configuration
@EnableConfigurationProperties(IsbnLookupProperties.class)
class BookLookupConfiguration {

    @Bean
    BookLookup bookLookup(IsbnLookupProperties properties, Clock clock) {
        return properties.enabled() ? new OpenLibraryBookLookup(properties, clock) : new NoBookLookup();
    }
}
