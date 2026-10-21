package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.ReleaseCopyReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires one reservation in a transaction of its own (RF19, RN13, RNF16). It is a bean apart from
 * {@link ExpireReservationsService} so that each call goes through the transaction proxy: a failure here rolls
 * back this reservation only. It only orchestrates: the domain closes the reservation ({@code Reservation.expire},
 * only after the deadline), the catalog gives the copy back to the shelf, and the {@link ReservationQueue} passes
 * it on to the next of the queue, whose deadline starts today.
 */
@Service
@Transactional
class ReservationExpirer {

    private final LibraryConfigurationRepository configurationRepository;
    private final ReservationRepository reservationRepository;
    private final ReleaseCopyReservationUseCase releaseCopyReservation;
    private final ReservationQueue reservationQueue;

    ReservationExpirer(LibraryConfigurationRepository configurationRepository,
            ReservationRepository reservationRepository, ReleaseCopyReservationUseCase releaseCopyReservation,
            ReservationQueue reservationQueue) {
        this.configurationRepository = configurationRepository;
        this.reservationRepository = reservationRepository;
        this.releaseCopyReservation = releaseCopyReservation;
        this.reservationQueue = reservationQueue;
    }

    /**
     * Expires the reservation if it still holds a copy past its deadline, as of {@code today}.
     *
     * @return whether it was expired; false when there was nothing to do, because it was picked up, cancelled or
     *         expired in the meantime, or its deadline has not passed (the last day is still in time)
     */
    boolean expire(Long reservationId, LocalDate today) {
        var reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || !reservation.isPastDeadline(today)) {
            return false;
        }
        var copyId = reservation.getCopyId();

        reservation.expire(today);
        reservationRepository.save(reservation);
        releaseCopyReservation.releaseReservation(copyId);
        reservationQueue.passTheTurn(copyId, today, configurationRepository.get().getReservationPickupDays());
        return true;
    }
}
