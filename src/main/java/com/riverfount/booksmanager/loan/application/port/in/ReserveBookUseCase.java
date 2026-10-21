package com.riverfount.booksmanager.loan.application.port.in;

/** Reserves a book with no copy available for a reader, putting them at the end of the queue (RF16). */
public interface ReserveBookUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the book or the reader
     *         does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException with every reason when the
     *         reservation is not allowed (RN05, RN11, RN12), including a reservation of the same book
     *         that another request made first
     */
    ReserveBookResult reserve(ReserveBookCommand command);
}
