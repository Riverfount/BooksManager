package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import com.riverfount.booksmanager.catalog.application.port.out.BookInfo;
import com.riverfount.booksmanager.catalog.application.port.out.BookLookup;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import java.util.Optional;

/** Used while the lookup is off (the default and the tests): it knows nothing and never goes to the network. */
class NoBookLookup implements BookLookup {

    @Override
    public Optional<BookInfo> findByIsbn(Isbn isbn) {
        return Optional.empty();
    }
}
