package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

/** Lists reservations by reader, book and situation, paginated (RF16, RNF14). */
public interface ListReservationsUseCase {

    PageResult<ReservationDetails> list(ListReservationsQuery query);
}
