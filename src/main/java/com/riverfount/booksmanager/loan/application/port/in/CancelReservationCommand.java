package com.riverfount.booksmanager.loan.application.port.in;

/** The cancellation of a reservation (RF17), done by an operator or an administrator in the name of the reader. */
public record CancelReservationCommand(Long reservationId) {
}
