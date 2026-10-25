package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.shared.domain.PageQuery;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * Book JPA entity, mapped to the table created by migration
 * V1__create_catalog.sql. The domain only keeps the authors' IDs, so
 * authorIds is mapped as a value collection in the book_author table,
 * not as a relationship to AuthorJpaEntity. The ISBN is stored as a
 * String; the conversion to/from Isbn is done by BookPersistenceAdapter.
 * The {@code @Version} is the optimistic lock (RNF17): the domain does not carry it. The adapter changes the entity
 * that the use case already loaded, in the same transaction, so the version read at the start is the one checked
 * when it is saved.
 */
@Entity
@Table(name = "book")
class BookJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false)
    private String title;

    private String publisher;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    // loads the authors of up to a whole page (max 100 books, RNF14) in a
    // single query, avoiding one query per book in listings (N+1); a
    // fetch join would make Hibernate paginate in memory
    @ElementCollection
    @BatchSize(size = PageQuery.MAX_SIZE)
    @CollectionTable(name = "book_author", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "author_id")
    private Set<Long> authorIds = new HashSet<>();

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Long version;

    protected BookJpaEntity() {
        // required by JPA
    }

    BookJpaEntity(Long id, String isbn, String title, String publisher, Integer publicationYear, Long categoryId,
            Set<Long> authorIds, boolean active) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.categoryId = categoryId;
        this.authorIds = new HashSet<>(authorIds);
        this.active = active;
    }

    /** Copies what a book can change after it exists; the ISBN never changes (RN01). */
    void update(String title, String publisher, Integer publicationYear, Long categoryId, Set<Long> authorIds,
            boolean active) {
        this.title = title;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.categoryId = categoryId;
        // the same set is kept when nothing changes, so the version does not move for nothing
        if (!this.authorIds.equals(authorIds)) {
            this.authorIds.clear();
            this.authorIds.addAll(authorIds);
        }
        this.active = active;
    }

    Long getId() {
        return id;
    }

    String getIsbn() {
        return isbn;
    }

    String getTitle() {
        return title;
    }

    String getPublisher() {
        return publisher;
    }

    Integer getPublicationYear() {
        return publicationYear;
    }

    Long getCategoryId() {
        return categoryId;
    }

    Set<Long> getAuthorIds() {
        return authorIds;
    }

    boolean isActive() {
        return active;
    }
}
