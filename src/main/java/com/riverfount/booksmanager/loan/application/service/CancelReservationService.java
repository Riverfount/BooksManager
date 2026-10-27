package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.catalog.application.port.in.ReleaseCopyReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationResult;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels a reservation (RF17). It only orchestrates: loads the reservation, lets the domain close it
 * ({@link Reservation#cancel}: a reservation already picked up, expired or cancelled is refused there) and
 * persists. When the reservation was holding a copy, the copy is given back to the shelf through the public
 * port of the catalog and then passed on to the next reservation of the queue, or left available when nobody
 * waits ({@link ReservationQueue}, RF18). It is one transaction (RNF16): if any step fails, nothing stays. Two
 * requests on the same reservation do not both pass (RNF17), because the reservation is versioned. The cancellation is reported to the audit trail (RF27), after the domain
 * has accepted it and in the same transaction, the same for the desk and for the reader (the author tells them
 * apart); the entry names the book and whether a copy was held, never the reader or the asset code.
 */
@Service
@Transactional
class CancelReservationService implements CancelReservationUseCase {

    private final LibraryConfigurationRepository configurationRepository;
    private final ReservationRepository reservationRepository;
    private final ReleaseCopyReservationUseCase releaseCopyReservation;
    private final ReservationQueue reservationQueue;
    private final Clock clock;
    private final RecordAuditEventUseCase audit;

    CancelReservationService(LibraryConfigurationRepository configurationRepository,
            ReservationRepository reservationRepository, ReleaseCopyReservationUseCase releaseCopyReservation,
            ReservationQueue reservationQueue, Clock clock, RecordAuditEventUseCase audit) {
        this.configurationRepository = configurationRepository;
        this.reservationRepository = reservationRepository;
        this.releaseCopyReservation = releaseCopyReservation;
        this.reservationQueue = reservationQueue;
        this.clock = clock;
        this.audit = audit;
    }

    @Override
    public CancelReservationResult cancel(CancelReservationCommand command) {
        var reservation = reservationRepository.findById(command.reservationId()).orElseThrow(
                () -> new ResourceNotFoundException("reserva não encontrada: " + command.reservationId()));
        var heldCopyId = reservation.isHoldingCopy() ? reservation.getCopyId() : null;

        reservation.cancel();
        var saved = reservationRepository.save(reservation);
        audit.record(new AuditEvent(AuditAction.RESERVATION_CANCELLED, AuditTarget.RESERVATION, saved.getId(),
                "livro " + saved.getBookId() + (heldCopyId != null ? ", com exemplar reservado" : ", na fila")));

        Reservation served = null;
        if (heldCopyId != null) {
            releaseCopyReservation.releaseReservation(heldCopyId);
            var pickupDays = configurationRepository.get().getReservationPickupDays();
            served = reservationQueue.passTheTurn(heldCopyId, LocalDate.now(clock), pickupDays).orElse(null);
        }
        return new CancelReservationResult(saved, served);
    }
}
