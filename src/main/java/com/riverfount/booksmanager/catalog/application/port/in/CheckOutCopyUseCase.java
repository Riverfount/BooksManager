package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Public port for the loan module: marks a copy as borrowed. The BORROWED status only
 * changes through the loan flow (RN03), never by the manual status change.
 */
public interface CheckOutCopyUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy is not active and
     *         available (RN03)
     * @throws org.springframework.dao.OptimisticLockingFailureException if another change to the copy got
     *         there first (RNF17)
     */
    void checkOut(Long copyId);
}
