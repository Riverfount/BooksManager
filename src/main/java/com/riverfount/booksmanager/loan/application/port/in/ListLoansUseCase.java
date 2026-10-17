package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

/** Lists loans by reader, copy and situation, paginated (RF14, HU30, RNF14). */
public interface ListLoansUseCase {

    PageResult<LoanDetails> list(ListLoansQuery query);
}
