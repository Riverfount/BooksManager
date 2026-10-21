package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Reservation;

/**
 * What a cancellation came to (RF17, RF18): the reservation, now closed, and the reservation that got the
 * copy it was holding, if any.
 *
 * @param reservation the cancelled reservation
 * @param served the next reservation of the queue, which received the freed copy; null when the cancelled
 *        reservation held no copy, or nobody was waiting (the copy went back to the shelf)
 */
public record CancelReservationResult(Reservation reservation, Reservation served) {
}
