package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.domain.LoanNoticeKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * A notice already sent about a loan, mapped to the table created by migration V11__add_loan_notices.sql. It is only
 * ever inserted: what was told to the reader is never changed. The loan is a plain id, as in the domain.
 */
@Entity
@Table(name = "loan_notice")
class LoanNoticeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private LoanNoticeKind kind;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    private int band;

    @Column(name = "sent_on", nullable = false, updatable = false)
    private LocalDate sentOn;

    protected LoanNoticeJpaEntity() {
        // required by JPA
    }

    LoanNoticeJpaEntity(Long loanId, LoanNoticeKind kind, LocalDate dueDate, int band, LocalDate sentOn) {
        this.loanId = loanId;
        this.kind = kind;
        this.dueDate = dueDate;
        this.band = band;
        this.sentOn = sentOn;
    }

    Long getLoanId() {
        return loanId;
    }

    LoanNoticeKind getKind() {
        return kind;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    int getBand() {
        return band;
    }
}
