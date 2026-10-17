package com.riverfount.booksmanager.catalog.application.port.out;

/** One row of the catalog count: a category, whether it is active, its active books and its active copies. */
public record CategoryCopyCount(Long categoryId, String categoryName, boolean categoryActive, long books,
        long copies) {
}
