package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.LoanNotice;

/** A notice already sent about a loan (RF28). */
public record SentLoanNotice(Long loanId, LoanNotice notice) {
}
