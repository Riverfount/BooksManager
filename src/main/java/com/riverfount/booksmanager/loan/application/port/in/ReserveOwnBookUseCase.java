package com.riverfount.booksmanager.loan.application.port.in;

/** A reader reserves a book for themselves (Phase 6, RF16), under the same rules as the reservation made at the desk. */
public interface ReserveOwnBookUseCase {

    /**
     * @return the new reservation as the reader sees it, with the place in the queue
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the book does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException with every reason when the
     *         reservation is not allowed (RN05, RN11, RN12)
     */
    ReservationDetails reserve(ReserveOwnBookCommand command);
}
