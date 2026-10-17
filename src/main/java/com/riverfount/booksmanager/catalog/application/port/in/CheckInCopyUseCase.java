package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Public port for the loan module: marks a returned copy as available again. A damaged
 * or lost copy is not made available by coming back.
 */
public interface CheckInCopyUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy was not borrowed
     * @throws org.springframework.dao.OptimisticLockingFailureException if another change to the copy got
     *         there first (RNF17)
     */
    void checkIn(Long copyId);
}
