package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Reservation;

/**
 * What a reservation came to (RF16): the reservation as stored and the place it took in the queue of
 * the book, counting from one.
 */
public record ReserveBookResult(Reservation reservation, long queuePosition) {
}
