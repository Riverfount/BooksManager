package com.riverfount.booksmanager.loan.application.port.in;

/**
 * A reader cancels a reservation of their own (Phase 6, RF17), whether it waits in the queue or holds a copy, with the
 * same effect as at the desk: a copy it held goes to the next of the queue or back to the shelf (RF18).
 */
public interface CancelOwnReservationUseCase {

    /**
     * @return the cancelled reservation. Never the reservation of the reader who received the freed copy, since it
     *         belongs to someone else.
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reservation does not exist
     *         or belongs to another reader (checked before anything else, so the state of the reservation of
     *         another reader is never told)
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the reservation is already closed
     */
    ReservationDetails cancel(CancelOwnReservationCommand command);
}
