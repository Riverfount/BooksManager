package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * What the loan module may know about a book when it is reserved (RN12), without reaching the
 * catalog's classes: whether the book exists is the absence of this answer, and
 * {@code hasAvailableCopy} tells if some active copy of it can be borrowed now, which makes the
 * book a case for lending, not for reserving. {@code bookActive} tells if the book is still active (RN14): a book
 * that was inactivated is neither lent nor reserved.
 */
public record BookAvailability(Long bookId, boolean hasAvailableCopy, boolean bookActive) {
}
