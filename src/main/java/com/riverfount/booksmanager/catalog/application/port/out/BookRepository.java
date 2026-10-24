package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for Book persistence, in the domain's language.
 */
public interface BookRepository {

    Book save(Book book);

    Optional<Book> findById(Long id);

    /**
     * The titles of the books with the given ids, in one query and without loading the books; an id
     * that does not exist is absent from the map.
     */
    Map<Long, String> findTitlesByIds(Set<Long> ids);

    boolean existsByIsbn(Isbn isbn);

    /**
     * Whether the book exists and is active (RN14), without loading the book: the loan module asks it on every
     * loan, reservation and return, and only the flag is needed.
     */
    boolean existsActiveById(Long id);

    /** The id of the book with the ISBN, active or not (the ISBN is unique among all), or empty. */
    Optional<Long> findIdByIsbn(Isbn isbn);

    /**
     * Lists books by active flag, narrowed by the optional filters (RF05).
     * The sort field is one of ListBooksQuery.SORTABLE_FIELDS.
     */
    PageResult<Book> findAll(BookFilter filter, boolean active, PageQuery page);
}
