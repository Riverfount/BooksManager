package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.shared.domain.PageResult;

/** Lists fines by reader and situation, paginated (RF22, HU11, RNF14). */
public interface ListFinesUseCase {

    PageResult<Fine> list(ListFinesQuery query);
}
