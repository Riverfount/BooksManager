package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.List;

/**
 * The answer to "what is known about this ISBN" (RF06): a suggestion to fill the registration of a book, never a
 * registered book. It always answers, whatever happened with the outside source (RNF19); {@code outcome} says which
 * of the three it was, and the suggestion is empty unless it was {@code FOUND}.
 *
 * @param isbn the ISBN as the catalog keeps it (no hyphens or spaces)
 * @param registeredBookId the book that already has this ISBN, when {@code outcome} is {@code ALREADY_REGISTERED}
 * @param suggestion title, publisher, year and authors for the registration, when {@code outcome} is {@code FOUND}
 */
public record BookLookupResult(String isbn, Outcome outcome, Long registeredBookId, Suggestion suggestion) {

    public enum Outcome {
        /** The outside source knows the ISBN: the suggestion is filled. */
        FOUND,
        /** Nothing came back: the source does not know the ISBN, is off, slow or down. The manual registration goes on. */
        NOT_FOUND,
        /** A book with this ISBN is already in the collection (the ISBN is unique), so nothing was asked outside. */
        ALREADY_REGISTERED
    }

    /** A suggested author: the name as the source gives it and, when the catalog has an active author of that name, its id. */
    public record SuggestedAuthor(String name, Long existingAuthorId) {
    }

    public record Suggestion(String title, String publisher, Integer publicationYear, List<SuggestedAuthor> authors) {

        public Suggestion {
            authors = authors == null ? List.of() : List.copyOf(authors);
        }
    }

    public static BookLookupResult found(String isbn, Suggestion suggestion) {
        return new BookLookupResult(isbn, Outcome.FOUND, null, suggestion);
    }

    public static BookLookupResult notFound(String isbn) {
        return new BookLookupResult(isbn, Outcome.NOT_FOUND, null, null);
    }

    public static BookLookupResult alreadyRegistered(String isbn, Long bookId) {
        return new BookLookupResult(isbn, Outcome.ALREADY_REGISTERED, bookId, null);
    }
}
