package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Fine;

/** Registers the payment of a fine (RF21, HU10); a paid fine stops blocking new loans (RN05). */
public interface PayFineUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the fine does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the fine was already paid or
     *         waived
     * @throws org.springframework.dao.OptimisticLockingFailureException if another request changed the fine first
     */
    Fine pay(Long fineId);
}
