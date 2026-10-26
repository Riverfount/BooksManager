package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.PageQuery;

/**
 * The reservations of the reader (Phase 6, RF16, RNF14), by situation. The reader is part of the query, so there is
 * no way to list the reservations of somebody else; a null situation means all of them, the closed ones included.
 */
public record ListOwnReservationsQuery(Long readerId, ReservationStatus status, PageQuery page) {
}
