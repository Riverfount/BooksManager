package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Suggests the data of a book from its ISBN, to save typing at the registration (RF06). It only consults: the book is
 * registered by {@link RegisterBookUseCase}, as always.
 */
public interface LookupBookByIsbnUseCase {

    /**
     * @param isbn the ISBN as typed, with or without hyphens
     * @throws com.riverfount.booksmanager.shared.domain.InvalidQueryException if it is not a valid ISBN (the check
     *         digit included), before anything is asked outside
     */
    BookLookupResult lookup(String isbn);
}
