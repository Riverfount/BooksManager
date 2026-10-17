package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * What a category holds, for the dashboard (RF25): its active books and the active copies of its books.
 * The copies of an inactive book are still copies of the library, so they count here, which makes the
 * copies of all the categories add up to the total of active copies.
 */
public record CategoryCopies(Long categoryId, String categoryName, long books, long copies) {
}
