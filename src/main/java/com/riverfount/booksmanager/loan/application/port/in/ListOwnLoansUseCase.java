package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

/** Lists the loans of the reader, paginated (Phase 6, RF14, RNF14). */
public interface ListOwnLoansUseCase {

    PageResult<LoanDetails> list(ListOwnLoansQuery query);
}
