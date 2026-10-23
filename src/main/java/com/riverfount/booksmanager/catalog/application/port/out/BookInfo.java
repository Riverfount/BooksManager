package com.riverfount.booksmanager.catalog.application.port.out;

import java.util.List;

/**
 * What an outside source knows about a book (RF06), in the catalog's language and nothing of the source's format: the
 * title, the authors' names, the publisher and the year of publication. Only the title is certain; the rest may be
 * empty or absent. Names, not entities: it is a suggestion to fill the registration, never a registered book.
 */
public record BookInfo(String title, List<String> authors, String publisher, Integer publicationYear) {

    public BookInfo {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("BookInfo title must not be blank");
        }
        authors = authors == null ? List.of() : List.copyOf(authors);
    }
}
