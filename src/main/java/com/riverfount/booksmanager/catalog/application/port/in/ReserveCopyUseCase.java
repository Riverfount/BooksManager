package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Public port for the loan module: keeps an available copy for a reader in the queue (RF16, RF18).
 * The RESERVED status only changes through the reservation and loan flows, never by the manual
 * status change.
 */
public interface ReserveCopyUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy is not active and
     *         available (RN03)
     * @throws org.springframework.dao.OptimisticLockingFailureException if another change to the copy got
     *         there first (RNF17)
     */
    void reserve(Long copyId);
}
