package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.BookFilter;
import com.riverfount.booksmanager.catalog.application.port.out.BookRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * In-memory implementation of BookRepository, used only in the
 * application services' unit tests (no Spring, no database). Does not
 * need to be thread-safe: it is a test fake, used from a single thread.
 */
class InMemoryBookRepository implements BookRepository {

    private final Map<Long, Book> books = new HashMap<>();
    private final InMemoryAuthorRepository authorRepository;
    private long nextId = 1;

    InMemoryBookRepository() {
        this(null);
    }

    /**
     * The author repository lets the fake filter by author name, which the
     * real adapter does with a join.
     */
    InMemoryBookRepository(InMemoryAuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

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
    public Map<Long, String> findTitlesByIds(Set<Long> ids) {
        return books.values().stream().filter(book -> ids.contains(book.getId()))
                .collect(Collectors.toMap(Book::getId, Book::getTitle));
    }

    @Override
    public boolean existsByIsbn(Isbn isbn) {
        return books.values().stream().anyMatch(book -> book.getIsbn().equals(isbn));
    }

    @Override
    public PageResult<Book> findAll(BookFilter filter, boolean active, PageQuery page) {
        var matching = books.values().stream()
                .filter(book -> book.isActive() == active)
                .filter(book -> contains(book.getTitle(), filter.title()))
                .filter(book -> contains(book.getIsbn().value(), filter.isbn()))
                .filter(book -> filter.categoryId() == null || filter.categoryId().equals(book.getCategoryId()))
                .filter(book -> filter.author() == null || authorNames(book).stream()
                        .anyMatch(name -> contains(name, filter.author())));
        return InMemoryPagination.page(matching, page, Map.of(
                "id", Comparator.comparing(Book::getId),
                "title", Comparator.comparing(Book::getTitle),
                "isbn", Comparator.comparing((Book book) -> book.getIsbn().value()),
                // like PostgreSQL: nulls last ascending, first descending
                "publicationYear", Comparator.comparing(Book::getPublicationYear,
                        Comparator.nullsLast(Comparator.naturalOrder()))),
                Comparator.comparing(Book::getId));
    }

    private Set<String> authorNames(Book book) {
        if (authorRepository == null) {
            return Set.of();
        }
        return authorRepository.findAllByIds(book.getAuthorIds()).stream()
                .map(Author::getName)
                .collect(Collectors.toSet());
    }

    private static boolean contains(String text, String term) {
        return term == null || text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }
}
