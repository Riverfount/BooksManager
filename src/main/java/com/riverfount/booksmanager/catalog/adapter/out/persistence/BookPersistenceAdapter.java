package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.application.port.out.BookFilter;
import com.riverfount.booksmanager.catalog.application.port.out.BookRepository;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.adapter.out.persistence.ConstraintViolations;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class BookPersistenceAdapter implements BookRepository {

    private final BookJpaRepository jpaRepository;

    BookPersistenceAdapter(BookJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Book save(Book book) {
        var entity = book.getId() == null ? create(book) : update(book);
        try {
            // saveAndFlush, not save: the optimistic lock (RNF17) and the unique ISBN fail here, synchronously
            var saved = jpaRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // defense against the race between the future service's
            // existsByIsbn() and this save(): the database's unique
            // constraint is what actually enforces RN01; here we only
            // translate it into the domain exception when the violation is
            // really the ISBN's — other violations (FK on a nonexistent
            // categoryId or authorId, for example) must not be confused
            // with a duplicate ISBN.
            if (ConstraintViolations.violates(e, "book_isbn_key")) {
                throw new BusinessRuleException("já existe um livro com o ISBN '" + book.getIsbn().value() + "'");
            }
            throw e;
        }
    }

    private static BookJpaEntity create(Book book) {
        return new BookJpaEntity(null, book.getIsbn().value(), book.getTitle(), book.getPublisher(),
                book.getPublicationYear(), book.getCategoryId(), book.getAuthorIds(), book.isActive());
    }

    /** Changes the entity that the use case loaded in this transaction, so its version is the one that is checked. */
    private BookJpaEntity update(Book book) {
        var entity = jpaRepository.findById(book.getId()).orElseThrow(
                () -> new IllegalStateException("the book " + book.getId() + " does not exist"));
        entity.update(book.getTitle(), book.getPublisher(), book.getPublicationYear(), book.getCategoryId(),
                book.getAuthorIds(), book.isActive());
        return entity;
    }

    @Override
    public Optional<Book> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Map<Long, String> findTitlesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findTitlesByIdIn(ids).stream()
                .collect(Collectors.toMap(BookJpaRepository.BookTitle::getId, BookJpaRepository.BookTitle::getTitle));
    }

    @Override
    public boolean existsByIsbn(Isbn isbn) {
        return jpaRepository.existsByIsbn(isbn.value());
    }

    @Override
    public boolean existsActiveById(Long id) {
        return jpaRepository.existsByIdAndActiveTrue(id);
    }

    @Override
    public Optional<Long> findIdByIsbn(Isbn isbn) {
        return jpaRepository.findIdByIsbn(isbn.value());
    }

    @Override
    public PageResult<Book> findAll(BookFilter filter, boolean active, PageQuery page) {
        var result = jpaRepository.search(LikePatterns.contains(filter.title()), LikePatterns.contains(filter.author()),
                LikePatterns.containsKeepingCase(filter.isbn()), filter.categoryId(), active,
                SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    /** "Contains" pattern, lowercased to match the lower() in the query; null stays null. */
    private Book toDomain(BookJpaEntity entity) {
        return Book.reconstitute(entity.getId(), new Isbn(entity.getIsbn()), entity.getTitle(),
                entity.getPublisher(), entity.getPublicationYear(), entity.getCategoryId(),
                entity.getAuthorIds(), entity.isActive());
    }
}
