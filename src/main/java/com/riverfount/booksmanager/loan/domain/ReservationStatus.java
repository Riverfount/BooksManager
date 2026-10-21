package com.riverfount.booksmanager.loan.domain;

/**
 * Where a reservation stands (RF16). Only {@code ACTIVE} can change; the other three are
 * final. Whether an active reservation waits in the queue or holds a copy is derived from
 * the copy it has, not stored here.
 */
public enum ReservationStatus {
    ACTIVE,
    FULFILLED,
    CANCELLED,
    EXPIRED
}
