package com.riverfount.booksmanager.loan.application.port.in;

/** Ends a suspension before its calculated end, with a justification (RN15, RF20). */
public interface EndSuspensionUseCase {

    /**
     * Ends the suspension from today on. Ending one that is already over, by itself or earlier,
     * changes nothing and answers with its current state; the justification is required either way.
     *
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the suspension does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the justification is missing or
     *         too long
     * @throws org.springframework.dao.OptimisticLockingFailureException if another request changed it first
     */
    SuspensionDetails end(EndSuspensionCommand command);
}
