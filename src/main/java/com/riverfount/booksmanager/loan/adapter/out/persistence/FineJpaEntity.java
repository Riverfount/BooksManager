package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.domain.FineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/**
 * Fine JPA entity, mapped to the table created by migration V7__create_loan.sql.
 * Separate from the domain; the conversion is done by FinePersistenceAdapter.
 */
@Entity
@Table(name = "fine")
class FineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Column(name = "reader_id", nullable = false, updatable = false)
    private Long readerId;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FineStatus status;

    @Column(name = "waiver_reason", length = 500)
    private String waiverReason;

    @Version
    @Column(nullable = false)
    private Long version;

    protected FineJpaEntity() {
        // required by JPA
    }

    FineJpaEntity(Long loanId, Long readerId, BigDecimal amount, FineStatus status, String waiverReason) {
        this.loanId = loanId;
        this.readerId = readerId;
        this.amount = amount;
        this.status = status;
        this.waiverReason = waiverReason;
    }

    /** Copies what a fine can change after it exists: its situation and the waiver reason. */
    void update(FineStatus status, String waiverReason) {
        this.status = status;
        this.waiverReason = waiverReason;
    }

    Long getId() {
        return id;
    }

    Long getLoanId() {
        return loanId;
    }

    Long getReaderId() {
        return readerId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    FineStatus getStatus() {
        return status;
    }

    String getWaiverReason() {
        return waiverReason;
    }
}
