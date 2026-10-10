package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.Set;

public record RegisterBookCommand(String isbn, String title, String publisher, Integer publicationYear,
        Long categoryId, Set<Long> authorIds) {
}
