package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Author JPA entity, mapped to the table created by migration
 * V1__create_catalog.sql. Separate from the domain; the conversion is
 * done by AuthorPersistenceAdapter.
 */
@Entity
@Table(name = "author")
class AuthorJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected AuthorJpaEntity() {
        // required by JPA
    }

    AuthorJpaEntity(Long id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return active;
    }
}
