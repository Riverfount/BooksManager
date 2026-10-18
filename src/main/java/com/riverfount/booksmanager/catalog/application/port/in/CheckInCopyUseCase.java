package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.CopyReturnCondition;

/**
 * Public port for the loan module: records a returned copy. In good condition it becomes
 * available again; damaged or lost (RF15) it becomes DAMAGED or LOST and never goes back on the
 * shelf by itself. A copy that was already damaged or lost is not made available by coming back.
 */
public interface CheckInCopyUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy was not borrowed
     * @throws org.springframework.dao.OptimisticLockingFailureException if another change to the copy got
     *         there first (RNF17)
     */
    void checkIn(Long copyId);

    /**
     * Same as {@link #checkIn(Long)}, but with the condition the copy came back in (RF15), in the
     * catalog's own type so that the catalog does not depend on the loan.
     *
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy was not borrowed
     * @throws org.springframework.dao.OptimisticLockingFailureException if another change to the copy got
     *         there first (RNF17)
     */
    void checkIn(Long copyId, CopyReturnCondition condition);
}
