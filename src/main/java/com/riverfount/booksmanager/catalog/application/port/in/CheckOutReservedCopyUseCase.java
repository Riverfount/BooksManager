package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Public port for the loan module: marks as borrowed the copy that was kept for the reader who now
 * picks it up (RF17). The ordinary {@link CheckOutCopyUseCase} keeps refusing a reserved copy.
 */
public interface CheckOutReservedCopyUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy is not reserved, or
     *         is not active
     * @throws org.springframework.dao.OptimisticLockingFailureException if another change to the copy got
     *         there first (RNF17)
     */
    void checkOutReserved(Long copyId);
}
