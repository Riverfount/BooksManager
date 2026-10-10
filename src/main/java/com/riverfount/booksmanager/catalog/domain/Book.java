package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Year;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Book of the collection (RF01, RF02). References the category and the
 * authors by identifier, not by object, keeping the aggregate small.
 * Books with loan history are deactivated, never physically deleted
 * (RN14).
 */
public class Book {

    public static final int MAX_TITLE_LENGTH = 255;

    private final Long id;
    private Isbn isbn;
    private String title;
    private String publisher;
    private Integer publicationYear;
    private Long categoryId;
    private final Set<Long> authorIds;
    private boolean active;

    private Book(Long id, Isbn isbn, String title, String publisher, Integer publicationYear,
            Long categoryId, Set<Long> authorIds, boolean active) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.categoryId = categoryId;
        this.authorIds = new HashSet<>(Objects.requireNonNull(authorIds, "authorIds não pode ser nulo"));
        this.active = active;
    }

    public static Book create(Isbn isbn, String title, String publisher, Integer publicationYear,
            Long categoryId, Set<Long> authorIds, Clock clock) {
        validateIsbn(isbn);
        validateTitle(title);
        validateCategory(categoryId);
        validateAuthors(authorIds);
        validatePublicationYear(publicationYear, clock);
        return new Book(null, isbn, title, publisher, publicationYear, categoryId, authorIds, true);
    }

    public static Book reconstitute(Long id, Isbn isbn, String title, String publisher, Integer publicationYear,
            Long categoryId, Set<Long> authorIds, boolean active) {
        return new Book(id, isbn, title, publisher, publicationYear, categoryId, authorIds, active);
    }

    /**
     * Decides whether the ISBN can be used, given whether another book
     * already has it. Whoever fetches that fact (the application, via the
     * repository) does not decide; it only brings the fact for the domain
     * to decide.
     */
    public static void validateUniqueIsbn(Isbn isbn, boolean alreadyExistsWithThisIsbn) {
        if (alreadyExistsWithThisIsbn) {
            throw new BusinessRuleException("já existe um livro com o ISBN '" + isbn.value() + "'");
        }
    }

    public void updateDetails(String title, String publisher, Integer publicationYear, Long categoryId,
            Clock clock) {
        validateTitle(title);
        validateCategory(categoryId);
        validatePublicationYear(publicationYear, clock);
        this.title = title;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.categoryId = categoryId;
    }

    public void addAuthor(Long authorId) {
        if (authorId == null) {
            throw new BusinessRuleException("o id do autor é obrigatório");
        }
        authorIds.add(authorId);
    }

    public void removeAuthor(Long authorId) {
        if (authorId == null) {
            throw new BusinessRuleException("o id do autor é obrigatório");
        }
        if (authorIds.size() == 1 && authorIds.contains(authorId)) {
            throw new BusinessRuleException("o livro precisa ter ao menos um autor");
        }
        authorIds.remove(authorId);
    }

    public void deactivate() {
        this.active = false;
    }

    private static void validateIsbn(Isbn isbn) {
        if (isbn == null) {
            throw new BusinessRuleException("o ISBN do livro é obrigatório");
        }
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("o título do livro é obrigatório");
        }
        if (!title.equals(title.strip())) {
            throw new BusinessRuleException("o título do livro não pode ter espaços nas pontas");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessRuleException(
                    "o título do livro deve ter até " + MAX_TITLE_LENGTH + " caracteres");
        }
    }

    private static void validateCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessRuleException("a categoria do livro é obrigatória");
        }
    }

    private static void validateAuthors(Set<Long> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            throw new BusinessRuleException("o livro precisa ter ao menos um autor");
        }
    }

    private static void validatePublicationYear(Integer publicationYear, Clock clock) {
        if (publicationYear != null && publicationYear > Year.now(clock).getValue()) {
            throw new BusinessRuleException("o ano de publicação não pode ser futuro");
        }
    }

    public Long getId() {
        return id;
    }

    public Isbn getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getPublisher() {
        return publisher;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Set<Long> getAuthorIds() {
        return Set.copyOf(authorIds);
    }

    public boolean isActive() {
        return active;
    }
}
