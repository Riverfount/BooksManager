package com.riverfount.booksmanager.loan.application.port.in;

/**
 * Expires the reservations that hold a copy and were not picked up by the end of the pickup deadline, and
 * passes each freed copy on to the next of the queue (RF19). It does not depend on the scheduled routine, which
 * is only a trigger for it, so it can be tested without a real clock. It is idempotent: running it again changes
 * nothing, because an expired reservation no longer holds a copy.
 */
public interface ExpireReservationsUseCase {

    /**
     * Expires every reservation whose last day to pick the copy up is before today (the deadline is inclusive),
     * each one in a transaction of its own, so that one that fails does not hold the others back.
     *
     * @return how many reservations were expired in this run
     */
    int expireOverdue();
}
