package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
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
    public static final int MAX_PUBLISHER_LENGTH = 255;
    /** The oldest year accepted: the press of movable type (Gutenberg), the start of the printed book. */
    public static final int MIN_PUBLICATION_YEAR = 1450;

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
        validatePublisherRequired(publisher);
        validatePublisher(publisher);
        validatePublicationYearRequired(publicationYear);
        validatePublicationYear(publicationYear, clock);
        return new Book(null, isbn, TitleCase.normalize(title),
                TitleCase.normalizePublisher(publisher), publicationYear, categoryId, authorIds, true);
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
        validatePublisher(publisher);
        validatePublicationYear(publicationYear, clock);
        this.title = TitleCase.normalize(title);
        this.publisher = TitleCase.normalizePublisher(publisher);
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

    /**
     * Replaces the whole set of authors at once, keeping the rule that a
     * book always has at least one author.
     */
    public void replaceAuthors(Set<Long> newAuthorIds) {
        validateAuthors(newAuthorIds);
        if (newAuthorIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessRuleException("o id do autor é obrigatório");
        }
        authorIds.clear();
        authorIds.addAll(newAuthorIds);
    }

    public void deactivate() {
        this.active = false;
    }

    /**
     * Brings an inactivated book back (RN14). The book is only reactivated when its category and all its authors
     * are active: reactivating is a decision about the collection, and an active book tied to an inactive
     * category or author would contradict the rule that those are not used for books (HU18, HU21). Whoever calls
     * brings the facts; every inactive one is named, so the operator reactivates them in one go. Reactivating an
     * active book changes nothing, whatever its category and authors are.
     *
     * @param category the category of the book
     * @param authors the authors of the book
     */
    public void reactivate(Category category, Collection<Author> authors) {
        Objects.requireNonNull(category, "category não pode ser nulo");
        Objects.requireNonNull(authors, "authors não pode ser nulo");
        if (active) {
            return;
        }
        var problems = new ArrayList<String>();
        if (!category.isActive()) {
            problems.add("categoria inativa: " + category.getName());
        }
        for (var author : authors) {
            if (!author.isActive()) {
                problems.add("autor inativo: " + author.getName());
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessRuleException("o livro não pode ser reativado (" + String.join("; ", problems)
                    + "): reative antes");
        }
        this.active = true;
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

    /** A new book has a publisher (RF01). The update does not ask for it again: a book that predates the rule may have none. */
    private static void validatePublisherRequired(String publisher) {
        if (publisher == null || publisher.isBlank()) {
            throw new BusinessRuleException("a editora do livro é obrigatória");
        }
    }

    /** A new book has a publication year (RF01). */
    private static void validatePublicationYearRequired(Integer publicationYear) {
        if (publicationYear == null) {
            throw new BusinessRuleException("o ano de publicação é obrigatório");
        }
    }

    /**
     * What a publisher may be, on creation and on update: never blank (it can be replaced, not emptied) and up to
     * the limit. Null is accepted here only because an update keeps the current value when it is not sent, and a
     * book that predates the rule may have none.
     */
    private static void validatePublisher(String publisher) {
        if (publisher != null && publisher.isBlank()) {
            throw new BusinessRuleException("a editora do livro não pode ficar em branco");
        }
        if (publisher != null && publisher.length() > MAX_PUBLISHER_LENGTH) {
            throw new BusinessRuleException(
                    "a editora do livro deve ter até " + MAX_PUBLISHER_LENGTH + " caracteres");
        }
    }

    private static void validatePublicationYear(Integer publicationYear, Clock clock) {
        if (publicationYear == null) {
            return;
        }
        if (publicationYear < MIN_PUBLICATION_YEAR) {
            throw new BusinessRuleException(
                    "o ano de publicação não pode ser anterior a " + MIN_PUBLICATION_YEAR);
        }
        if (publicationYear > Year.now(clock).getValue()) {
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
