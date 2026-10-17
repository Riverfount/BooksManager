package com.riverfount.booksmanager.loan.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * Suspension JPA entity, mapped to the table created by migration V7__create_loan.sql.
 * Separate from the domain; the conversion is done by SuspensionPersistenceAdapter.
 */
@Entity
@Table(name = "suspension")
class SuspensionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false, updatable = false)
    private Long readerId;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "ended_early_on")
    private LocalDate endedEarlyOn;

    @Column(name = "ended_early_reason", length = 500)
    private String endedEarlyReason;

    @Version
    @Column(nullable = false)
    private Long version;

    protected SuspensionJpaEntity() {
        // required by JPA
    }

    SuspensionJpaEntity(Long readerId, Long loanId, LocalDate startDate, LocalDate endDate, LocalDate endedEarlyOn,
            String endedEarlyReason) {
        this.readerId = readerId;
        this.loanId = loanId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.endedEarlyOn = endedEarlyOn;
        this.endedEarlyReason = endedEarlyReason;
    }

    /** Copies what a suspension can change after it exists: the early end and its justification. */
    void update(LocalDate endedEarlyOn, String endedEarlyReason) {
        this.endedEarlyOn = endedEarlyOn;
        this.endedEarlyReason = endedEarlyReason;
    }

    Long getId() {
        return id;
    }

    Long getReaderId() {
        return readerId;
    }

    Long getLoanId() {
        return loanId;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    LocalDate getEndedEarlyOn() {
        return endedEarlyOn;
    }

    String getEndedEarlyReason() {
        return endedEarlyReason;
    }
}
