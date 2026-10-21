package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.ReservationStatus;

/** Optional filters of the reservation listing (RF16); a null field means "no filter on that field". */
public record ReservationFilter(Long readerId, Long bookId, ReservationStatus status) {

    public static ReservationFilter none() {
        return new ReservationFilter(null, null, null);
    }
}
