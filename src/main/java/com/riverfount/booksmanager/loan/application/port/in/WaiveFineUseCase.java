package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Fine;

/** Waives a fine with a justification (RF21, HU10); a waived fine stops blocking new loans (RN05). */
public interface WaiveFineUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the fine does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the justification is missing or
     *         too long, or the fine was already paid or waived
     * @throws org.springframework.dao.OptimisticLockingFailureException if another request changed the fine first
     */
    Fine waive(WaiveFineCommand command);
}
