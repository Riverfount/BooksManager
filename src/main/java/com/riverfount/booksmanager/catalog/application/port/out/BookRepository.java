package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;

/**
 * Outbound port for Book persistence, in the domain's language.
 */
public interface BookRepository {

    Book save(Book book);

    Optional<Book> findById(Long id);

    boolean existsByIsbn(Isbn isbn);

    /**
     * Lists books by active flag. The sort field is one of
     * ListBooksQuery.SORTABLE_FIELDS.
     */
    PageResult<Book> findAll(boolean active, PageQuery page);
}
