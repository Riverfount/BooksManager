package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Public port for the loan module: gives back to the shelf a copy that was kept for a reservation
 * which ended without a pickup (cancelled or expired, RF17, RF19).
 */
public interface ReleaseCopyReservationUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy is not reserved
     * @throws org.springframework.dao.OptimisticLockingFailureException if another change to the copy got
     *         there first (RNF17)
     */
    void releaseReservation(Long copyId);
}
