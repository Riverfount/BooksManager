package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReserveCopyUseCase;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Passes the turn" of a copy that was just freed (RF18, RN13): hands it to the first reservation in the
 * queue of its book, or leaves it on the shelf when nobody waits. It is shared by the flows that free a
 * copy (the return of a loan, and the cancellation and the expiration of a reservation), and takes part in
 * the transaction of the caller: if any step fails, the caller's whole use case is undone (RNF16). It only
 * orchestrates: the catalog keeps the copy ({@code RESERVED}) and the {@link Reservation} sets its own
 * pickup deadline. The queue is in order of arrival; two copies of the same book freed at once go to
 * different readers, and a race for the same reservation fails on its version (409, RNF17).
 *
 * <p>The reader of the reservation served is told by e-mail (RF28): the book and the last day to pick the copy up.
 * The notice goes through {@link NotificationSender#send}, so it leaves only after the commit of the caller's
 * transaction, is dropped if that is rolled back, and a failure of the mail never reaches the caller (RNF19).
 */
@Service
@Transactional
class ReservationQueue {

    private final ReservationRepository reservationRepository;
    private final FindCopyAvailabilityUseCase copyAvailability;
    private final FindCopySummariesUseCase copySummaries;
    private final ReserveCopyUseCase reserveCopy;
    private final FindReaderContactsUseCase readerContacts;
    private final NotificationSender notificationSender;

    ReservationQueue(ReservationRepository reservationRepository, FindCopyAvailabilityUseCase copyAvailability,
            FindCopySummariesUseCase copySummaries, ReserveCopyUseCase reserveCopy,
            FindReaderContactsUseCase readerContacts, NotificationSender notificationSender) {
        this.reservationRepository = reservationRepository;
        this.copyAvailability = copyAvailability;
        this.copySummaries = copySummaries;
        this.reserveCopy = reserveCopy;
        this.readerContacts = readerContacts;
        this.notificationSender = notificationSender;
    }

    /**
     * Gives the copy to the first reservation waiting for its book: the copy becomes {@code RESERVED} and the
     * reservation gets {@code today} plus {@code pickupDays} as its deadline.
     *
     * @return the reservation served, or empty when the copy stays available: nobody waits for the book, or
     *         the copy is not one that can be lent (inactive, damaged or lost) and so cannot be kept for anyone
     */
    Optional<Reservation> passTheTurn(Long copyId, LocalDate today, int pickupDays) {
        var lendable = copyAvailability.findAvailability(copyId).map(availability -> availability.lendable());
        if (lendable.isEmpty() || !lendable.get()) {
            return Optional.empty();
        }
        var summary = copySummaries.findSummaries(Set.of(copyId)).get(copyId);
        if (summary == null) {
            return Optional.empty();
        }
        var first = reservationRepository.findFirstWaiting(summary.bookId());
        if (first.isEmpty()) {
            return Optional.empty();
        }
        var reservation = first.get();
        reserveCopy.reserve(copyId);
        reservation.assignCopy(copyId, today, pickupDays);
        var served = reservationRepository.save(reservation);
        notifyReader(served, summary.bookTitle());
        return Optional.of(served);
    }

    /** A reader without a contact is still served; there is just nobody to tell. */
    private void notifyReader(Reservation served, String bookTitle) {
        var contact = readerContacts.findContacts(Set.of(served.getReaderId())).get(served.getReaderId());
        if (contact != null) {
            notificationSender.send(
                    ReservationNoticeMessages.copyReady(contact, bookTitle, served.getPickupDeadline()));
        }
    }
}
