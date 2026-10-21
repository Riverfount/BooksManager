package com.riverfount.booksmanager.loan.application.port.in;

import java.util.Optional;

/** Finds a reservation, active or closed, with its place in the queue or its copy (RF16). */
public interface GetReservationUseCase {

    Optional<ReservationDetails> findById(Long id);
}
