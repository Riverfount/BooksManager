package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Category;
import java.util.List;

/**
 * A book together with what a screen needs to show it (RF05): how many of
 * its copies are available to borrow, how many active copies it has in
 * all, and the category and the authors by name (the Book aggregate only
 * holds their ids). The counts belong to the copies, not to the Book
 * aggregate, so they travel beside the book instead of inside it. Only
 * active copies are counted, and the available ones are those with status
 * AVAILABLE. The category and the authors are shown even when inactive (the
 * history keeps them, RN14); the authors come ordered by name.
 */
public record BookWithAvailability(Book book, long availableCopies, long totalCopies, Category category,
        List<Author> authors) {
}
