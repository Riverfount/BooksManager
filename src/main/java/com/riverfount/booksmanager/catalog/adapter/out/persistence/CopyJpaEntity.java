package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Copy JPA entity, mapped to the table created by migration
 * V1__create_catalog.sql. The {@code @Version} on version is the actual
 * optimistic lock (RNF17); the domain only carries that value.
 */
@Entity
@Table(name = "copy")
class CopyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "asset_code", nullable = false, unique = true)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopyStatus status;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Long version;

    protected CopyJpaEntity() {
        // required by JPA
    }

    CopyJpaEntity(Long id, Long bookId, String assetCode, CopyStatus status, boolean active,
            Long version) {
        this.id = id;
        this.bookId = bookId;
        this.assetCode = assetCode;
        this.status = status;
        this.active = active;
        this.version = version;
    }

    Long getId() {
        return id;
    }

    Long getBookId() {
        return bookId;
    }

    String getAssetCode() {
        return assetCode;
    }

    CopyStatus getStatus() {
        return status;
    }

    boolean isActive() {
        return active;
    }

    Long getVersion() {
        return version;
    }
}
