package com.riverfount.booksmanager.catalog.application.port.out;

import java.util.Locale;

/**
 * Optional filters of the book search (RF05); a null field means "no
 * filter on that field". Text filters match by "contains", ignoring case;
 * the category matches by id. Blank texts count as absent, and in the ISBN
 * hyphens and spaces are ignored, as in the registration.
 */
public record BookFilter(String title, String author, String isbn, Long categoryId) {

    public BookFilter {
        title = normalize(title);
        author = normalize(author);
        isbn = normalizeIsbn(isbn);
    }

    public static BookFilter none() {
        return new BookFilter(null, null, null, null);
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String normalizeIsbn(String isbn) {
        var normalized = normalize(isbn);
        if (normalized == null) {
            return null;
        }
        var digits = normalized.replaceAll("[-\\s]", "").toUpperCase(Locale.ROOT);
        return digits.isEmpty() ? null : digits;
    }
}
