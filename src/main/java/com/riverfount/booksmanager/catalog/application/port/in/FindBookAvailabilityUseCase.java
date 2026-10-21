package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.Optional;

/** Public port for the loan module: does this book have a copy that can be borrowed now? */
public interface FindBookAvailabilityUseCase {

    /** The availability of the book, empty when the book does not exist. */
    Optional<BookAvailability> findBookAvailability(Long bookId);
}
