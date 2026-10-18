package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.ReturnCondition;

/**
 * The return of a loan (RF12, HU08). The date is today, from the clock, and the penalty from the
 * configuration. The condition of the copy (RF15) is optional and counts as GOOD when missing.
 */
public record ReturnLoanCommand(Long loanId, ReturnCondition condition) {

    public ReturnLoanCommand {
        if (condition == null) {
            condition = ReturnCondition.GOOD;
        }
    }

    public ReturnLoanCommand(Long loanId) {
        this(loanId, ReturnCondition.GOOD);
    }
}
