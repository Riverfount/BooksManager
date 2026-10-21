package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * Reservation JPA entity, mapped to the table created by migration V10__create_reservation.sql.
 * Separate from the domain; the conversion is done by ReservationPersistenceAdapter. The book, the
 * reader and the copy are plain ids, as in the domain. The {@code @Version} is the optimistic lock of
 * the row; the domain does not carry it, because the adapter updates the entity loaded in the same
 * transaction.
 */
@Entity
@Table(name = "reservation")
class ReservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false, updatable = false)
    private Long bookId;

    @Column(name = "reader_id", nullable = false, updatable = false)
    private Long readerId;

    @Column(name = "reservation_date", nullable = false, updatable = false)
    private LocalDate reservationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "copy_id")
    private Long copyId;

    @Column(name = "pickup_deadline")
    private LocalDate pickupDeadline;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ReservationJpaEntity() {
        // required by JPA
    }

    ReservationJpaEntity(Long bookId, Long readerId, LocalDate reservationDate, ReservationStatus status,
            Long copyId, LocalDate pickupDeadline) {
        this.bookId = bookId;
        this.readerId = readerId;
        this.reservationDate = reservationDate;
        this.status = status;
        this.copyId = copyId;
        this.pickupDeadline = pickupDeadline;
    }

    /** Copies what a reservation can change after it exists: its status, the copy it holds and the deadline. */
    void update(ReservationStatus status, Long copyId, LocalDate pickupDeadline) {
        this.status = status;
        this.copyId = copyId;
        this.pickupDeadline = pickupDeadline;
    }

    Long getId() {
        return id;
    }

    Long getBookId() {
        return bookId;
    }

    Long getReaderId() {
        return readerId;
    }

    LocalDate getReservationDate() {
        return reservationDate;
    }

    ReservationStatus getStatus() {
        return status;
    }

    Long getCopyId() {
        return copyId;
    }

    LocalDate getPickupDeadline() {
        return pickupDeadline;
    }
}
