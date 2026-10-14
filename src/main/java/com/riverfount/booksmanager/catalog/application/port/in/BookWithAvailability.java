package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Book;

/**
 * A book together with how many of its copies are available to borrow
 * (RF05). The count belongs to the copies, not to the Book aggregate, so
 * it travels beside the book instead of inside it. Only active copies with
 * status AVAILABLE are counted.
 */
public record BookWithAvailability(Book book, long availableCopies) {
}
