package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

/** Lists the reservations of the reader, paginated (Phase 6, RF16, RNF14). */
public interface ListOwnReservationsUseCase {

    PageResult<ReservationDetails> list(ListOwnReservationsQuery query);
}
