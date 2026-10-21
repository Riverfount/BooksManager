package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.ExpireReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Expires the reservations past their pickup deadline (RF19). It is not transactional itself: it finds the
 * reservations that hold a copy past the deadline (today from the clock, the deadline is inclusive) and hands
 * each one to the {@link ReservationExpirer}, which works in a transaction of its own. So a reservation that
 * fails (a concurrent change, say) is logged and the others go on; it will be found again by the next run.
 * Running it twice in a row changes nothing the second time.
 */
@Service
class ExpireReservationsService implements ExpireReservationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireReservationsService.class);

    private final ReservationRepository reservationRepository;
    private final ReservationExpirer expirer;
    private final Clock clock;

    ExpireReservationsService(ReservationRepository reservationRepository, ReservationExpirer expirer, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.expirer = expirer;
        this.clock = clock;
    }

    @Override
    public int expireOverdue() {
        var today = LocalDate.now(clock);
        var expired = 0;
        for (var reservation : reservationRepository.findPastDeadline(today)) {
            try {
                if (expirer.expire(reservation.getId(), today)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire the reservation {}; it will be tried again in the next run",
                        reservation.getId(), e);
            }
        }
        return expired;
    }
}
