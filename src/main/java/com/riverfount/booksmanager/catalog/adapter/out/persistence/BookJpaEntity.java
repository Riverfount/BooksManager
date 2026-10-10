package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/**
 * Book JPA entity, mapped to the table created by migration
 * V1__create_catalog.sql. The domain only keeps the authors' IDs, so
 * authorIds is mapped as a value collection in the book_author table,
 * not as a relationship to AuthorJpaEntity. The ISBN is stored as a
 * String; the conversion to/from Isbn is done by BookPersistenceAdapter.
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

    @ElementCollection
    @CollectionTable(name = "book_author", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "author_id")
    private Set<Long> authorIds = new HashSet<>();

    @Column(nullable = false)
    private boolean active;

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
