package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.CancelOwnReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelOwnReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnReservationQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReservationDetails;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReserveOwnBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveOwnBookUseCase;
import com.riverfount.booksmanager.shared.domain.Ownership;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a reader does with their own reservations (Phase 6, RF16, RF17): the same use cases as at the desk, with the
 * reader fixed to the one of the token and the rule of the owner applied here, not in the controller. The rules of
 * reserving and of cancelling are the ones of those use cases; this only decides whose reservation it is and what
 * comes back.
 *
 * <p>A reservation of another reader is answered like one that does not exist ({@link Ownership}), and that is
 * checked before the cancellation runs, so a refusal such as "already cancelled" never tells the state of a
 * reservation that is not the reader's. A cancellation frees the copy for the next reservation of the queue, which
 * belongs to someone else: only the cancelled reservation goes back to the reader.
 */
@Service
@Transactional
class OwnReservationService implements ReserveOwnBookUseCase, GetOwnReservationUseCase, ListOwnReservationsUseCase,
        CancelOwnReservationUseCase {

    private final ReserveBookUseCase reserveBook;
    private final GetReservationUseCase getReservation;
    private final ListReservationsUseCase listReservations;
    private final CancelReservationUseCase cancelReservation;

    OwnReservationService(ReserveBookUseCase reserveBook, GetReservationUseCase getReservation,
            ListReservationsUseCase listReservations, CancelReservationUseCase cancelReservation) {
        this.reserveBook = reserveBook;
        this.getReservation = getReservation;
        this.listReservations = listReservations;
        this.cancelReservation = cancelReservation;
    }

    @Override
    public ReservationDetails reserve(ReserveOwnBookCommand command) {
        var result = reserveBook.reserve(new ReserveBookCommand(command.bookId(), command.readerId()));
        return detailsOf(result.reservation().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationDetails get(GetOwnReservationQuery query) {
        return ownDetails(query.reservationId(), query.readerId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ReservationDetails> list(ListOwnReservationsQuery query) {
        return listReservations.list(new ListReservationsQuery(query.readerId(), null, query.status(), query.page()));
    }

    @Override
    public ReservationDetails cancel(CancelOwnReservationCommand command) {
        ownDetails(command.reservationId(), command.readerId());
        cancelReservation.cancel(new CancelReservationCommand(command.reservationId()));
        return detailsOf(command.reservationId());
    }

    /** The details of the reservation, when it is the reader's; otherwise "not found", whatever the reason. */
    private ReservationDetails ownDetails(Long reservationId, Long readerId) {
        var notFound = "reserva não encontrada: " + reservationId;
        var details = getReservation.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(notFound));
        Ownership.requireOwner(details.reservation().getReaderId(), readerId, notFound);
        return details;
    }

    private ReservationDetails detailsOf(Long reservationId) {
        return getReservation.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("reservation vanished inside its own transaction"));
    }
}
