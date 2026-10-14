package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.Set;

/**
 * Partial update of a book (RF01, HU14): a null field means "keep the
 * current value". The ISBN is not editable (RN01), so it is not part of
 * the command.
 */
public record UpdateBookCommand(Long id, String title, String publisher, Integer publicationYear,
        Long categoryId, Set<Long> authorIds) {
}
