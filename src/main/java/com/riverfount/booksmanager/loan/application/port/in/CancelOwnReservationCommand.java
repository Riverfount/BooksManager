package com.riverfount.booksmanager.loan.application.port.in;

/** A reader cancels a reservation of their own (Phase 6, RF17). */
public record CancelOwnReservationCommand(Long reservationId, Long readerId) {
}
