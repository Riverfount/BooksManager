package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Isbn;
import java.util.Optional;

/**
 * Outbound port that asks an outside source for the data of a book by its ISBN (RF06), to save typing at the
 * registration. The registration by hand never depends on it (RNF19): an implementation never throws, and
 * "not found", "the service is off", "too slow" and "answered nonsense" all come back the same way, as empty.
 */
public interface BookLookup {

    /** The data the source knows about the ISBN, or empty when it knows nothing or could not be asked. */
    Optional<BookInfo> findByIsbn(Isbn isbn);
}
