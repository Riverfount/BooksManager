package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Category JPA entity, mapped to the table created by migration
 * V1__create_catalog.sql. Separate from the domain; the conversion is
 * done by CategoryPersistenceAdapter.
 */
@Entity
@Table(name = "category")
class CategoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected CategoryJpaEntity() {
        // required by JPA
    }

    CategoryJpaEntity(Long id, String name, boolean active) {
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
