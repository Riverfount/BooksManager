package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.BookRepository;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of BookRepository, used only in the
 * application services' unit tests (no Spring, no database). Does not
 * need to be thread-safe: it is a test fake, used from a single thread.
 */
class InMemoryBookRepository implements BookRepository {

    private final Map<Long, Book> books = new HashMap<>();
    private long nextId = 1;

    @Override
    public Book save(Book book) {
        var id = book.getId() != null ? book.getId() : nextId++;
        var saved = Book.reconstitute(id, book.getIsbn(), book.getTitle(), book.getPublisher(),
                book.getPublicationYear(), book.getCategoryId(), book.getAuthorIds(), book.isActive());
        books.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Book> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(books.get(id));
    }

    @Override
    public boolean existsByIsbn(Isbn isbn) {
        return books.values().stream().anyMatch(book -> book.getIsbn().equals(isbn));
    }
}
