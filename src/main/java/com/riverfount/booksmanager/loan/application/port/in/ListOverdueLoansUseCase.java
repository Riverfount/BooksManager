package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

/** The report of overdue loans, from the most to the least late, paginated (RF23, HU12, RNF14). */
public interface ListOverdueLoansUseCase {

    PageResult<OverdueLoan> list(ListOverdueLoansQuery query);
}
