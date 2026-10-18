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
 * Loan JPA entity, mapped to the table created by migration V7__create_loan.sql (and V9 for the return condition).
 * Separate from the domain; the conversion is done by LoanPersistenceAdapter. The
 * copy and the reader are plain ids, as in the domain. The {@code @Version} is the
 * optimistic lock of the row; the domain does not carry it, because the adapter
 * updates the entity loaded in the same transaction.
 */
@Entity
@Table(name = "loan")
class LoanJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "copy_id", nullable = false, updatable = false)
    private Long copyId;

    @Column(name = "reader_id", nullable = false, updatable = false)
    private Long readerId;

    @Column(name = "pickup_date", nullable = false, updatable = false)
    private LocalDate pickupDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "return_condition")
    private String returnCondition;

    @Column(nullable = false)
    private int renewals;

    @Version
    @Column(nullable = false)
    private Long version;

    protected LoanJpaEntity() {
        // required by JPA
    }

    LoanJpaEntity(Long copyId, Long readerId, LocalDate pickupDate, LocalDate dueDate, LocalDate returnDate,
            int renewals) {
        this.copyId = copyId;
        this.readerId = readerId;
        this.pickupDate = pickupDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.returnCondition = conditionOf(returnDate);
        this.renewals = renewals;
    }

    /** Copies what a loan can change after it exists: the due date, the return and the renewals. */
    void update(LocalDate dueDate, LocalDate returnDate, int renewals) {
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.returnCondition = conditionOf(returnDate);
        this.renewals = renewals;
    }

    /**
     * The database requires a condition with every return (V9). The domain only knows the date
     * until it carries the condition (RF15), so a return is recorded as GOOD meanwhile.
     */
    private static String conditionOf(LocalDate returnDate) {
        return returnDate == null ? null : "GOOD";
    }

    Long getId() {
        return id;
    }

    Long getCopyId() {
        return copyId;
    }

    Long getReaderId() {
        return readerId;
    }

    LocalDate getPickupDate() {
        return pickupDate;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    LocalDate getReturnDate() {
        return returnDate;
    }

    int getRenewals() {
        return renewals;
    }
}
