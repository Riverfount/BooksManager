package com.riverfount.booksmanager.loan.application.port.in;

/**
 * Cancels a reservation, whether it waits in the queue or holds a copy (RF17). A copy it was holding goes to the
 * next reservation of the queue, or back to the shelf when nobody waits (RF18).
 */
public interface CancelReservationUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reservation does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the reservation is already closed:
     *         picked up, expired or cancelled
     * @throws org.springframework.dao.OptimisticLockingFailureException if another request changed the
     *         reservation or its copy first (RNF17)
     */
    CancelReservationResult cancel(CancelReservationCommand command);
}
