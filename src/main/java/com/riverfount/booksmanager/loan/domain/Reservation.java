package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;

/**
 * The reservation of a book by a reader (RF16): a place in the queue for a book with no
 * copy available. It keeps the ids of the book, the reader and the copy, not the objects,
 * like the {@link Loan}.
 *
 * <p>While it is {@code ACTIVE} it is in one of two moments, both derived and neither
 * stored: waiting in the queue (no copy yet), or holding a copy for the reader until the
 * pickup deadline (RN13). Fulfilled, cancelled and expired are final: a closed reservation
 * never changes again.
 *
 * <p>The domain never reads the clock: the date of every operation comes by parameter, and
 * the pickup days come from the library configuration (RN16). The deadline is inclusive,
 * like the due date of a loan: on the last day the copy can still be picked up, and the
 * next day the reservation is past its deadline.
 */
public class Reservation {

    private final Long id;
    private final Long bookId;
    private final Long readerId;
    private final LocalDate reservationDate;
    private ReservationStatus status;
    private Long copyId;
    private LocalDate pickupDeadline;

    private Reservation(Long id, Long bookId, Long readerId, LocalDate reservationDate, ReservationStatus status,
            Long copyId, LocalDate pickupDeadline) {
        this.id = id;
        this.bookId = bookId;
        this.readerId = readerId;
        this.reservationDate = reservationDate;
        this.status = status;
        this.copyId = copyId;
        this.pickupDeadline = pickupDeadline;
    }

    /** A new active reservation, waiting in the queue with no copy (RF16). */
    public static Reservation create(Long bookId, Long readerId, LocalDate reservationDate) {
        validate(bookId, readerId, reservationDate);
        return new Reservation(null, bookId, readerId, reservationDate, ReservationStatus.ACTIVE, null, null);
    }

    /** A persisted reservation, checked for the same consistency the transitions keep. */
    public static Reservation reconstitute(Long id, Long bookId, Long readerId, LocalDate reservationDate,
            ReservationStatus status, Long copyId, LocalDate pickupDeadline) {
        validate(bookId, readerId, reservationDate);
        if (status == null) {
            throw new BusinessRuleException("a situação da reserva é obrigatória");
        }
        if ((copyId == null) != (pickupDeadline == null)) {
            throw new BusinessRuleException("o exemplar e o prazo de retirada da reserva andam juntos");
        }
        if ((status == ReservationStatus.FULFILLED || status == ReservationStatus.EXPIRED) && copyId == null) {
            throw new BusinessRuleException("a reserva retirada ou expirada tem exemplar");
        }
        if (pickupDeadline != null && pickupDeadline.isBefore(reservationDate)) {
            throw new BusinessRuleException("o prazo de retirada não pode ser anterior à reserva");
        }
        return new Reservation(id, bookId, readerId, reservationDate, status, copyId, pickupDeadline);
    }

    /**
     * Gives the reservation a copy to pick up (RN13): the deadline is {@code today} plus the
     * library's pickup days. Only an active reservation that holds no copy yet can take one.
     * Nothing changes when it is refused.
     *
     * @param pickupDays how many days the reader has to pick the copy up; at least one
     */
    public void assignCopy(Long copyId, LocalDate today, int pickupDays) {
        requireDate(today);
        if (pickupDays < 1) {
            throw new IllegalArgumentException("the pickup period must be at least one day");
        }
        requireActive();
        if (copyId == null) {
            throw new BusinessRuleException("o exemplar é obrigatório");
        }
        if (this.copyId != null) {
            throw new BusinessRuleException("a reserva já segura um exemplar");
        }
        if (today.isBefore(reservationDate)) {
            throw new BusinessRuleException("a atribuição do exemplar não pode ser anterior à data da reserva");
        }
        this.copyId = copyId;
        this.pickupDeadline = today.plusDays(pickupDays);
    }

    /**
     * Closes the reservation as picked up (RF17). Only one that holds a copy, and up to the
     * deadline inclusive. Nothing changes when it is refused.
     */
    public void fulfill(LocalDate today) {
        requireDate(today);
        requireActive();
        requireHoldingCopy();
        if (isPastDeadline(today)) {
            throw new BusinessRuleException("o prazo de retirada terminou em " + pickupDeadline);
        }
        this.status = ReservationStatus.FULFILLED;
    }

    /** Closes the reservation as cancelled (RF17), whether it waits in the queue or holds a copy. */
    public void cancel() {
        requireActive();
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * Closes the reservation as expired (RF19). Only one that holds a copy can expire, and
     * only after the deadline: on the last day it can still be picked up. Nothing changes
     * when it is refused.
     */
    public void expire(LocalDate today) {
        requireDate(today);
        requireActive();
        requireHoldingCopy();
        if (!isPastDeadline(today)) {
            throw new BusinessRuleException("o prazo de retirada vai até " + pickupDeadline);
        }
        this.status = ReservationStatus.EXPIRED;
    }

    /** Whether the reservation holds a copy and today is after the last day to pick it up. */
    public boolean isPastDeadline(LocalDate today) {
        requireDate(today);
        return isHoldingCopy() && today.isAfter(pickupDeadline);
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    /** Active and with no copy yet: it is in the queue of its book. */
    public boolean isWaiting() {
        return isActive() && copyId == null;
    }

    /** Active and with a copy kept for the reader until the deadline. */
    public boolean isHoldingCopy() {
        return isActive() && copyId != null;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new BusinessRuleException("a reserva já foi encerrada");
        }
    }

    private void requireHoldingCopy() {
        if (copyId == null) {
            throw new BusinessRuleException("a reserva ainda espera na fila e não tem exemplar");
        }
    }

    private static void requireDate(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("the date is required");
        }
    }

    private static void validate(Long bookId, Long readerId, LocalDate reservationDate) {
        if (bookId == null) {
            throw new BusinessRuleException("o livro é obrigatório");
        }
        if (readerId == null) {
            throw new BusinessRuleException("o leitor é obrigatório");
        }
        if (reservationDate == null) {
            throw new BusinessRuleException("a data da reserva é obrigatória");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public Long getReaderId() {
        return readerId;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    /** The copy kept for the reader, or null while the reservation waits in the queue. */
    public Long getCopyId() {
        return copyId;
    }

    /** The last day to pick the copy up, or null while the reservation has no copy. */
    public LocalDate getPickupDeadline() {
        return pickupDeadline;
    }
}
