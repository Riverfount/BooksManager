package com.riverfount.booksmanager.loan.application.port.in;

/** One reservation of the reader (Phase 6). */
public record GetOwnReservationQuery(Long reservationId, Long readerId) {
}
