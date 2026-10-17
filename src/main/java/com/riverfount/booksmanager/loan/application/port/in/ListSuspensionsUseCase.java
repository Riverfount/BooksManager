package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

/** Lists suspensions by reader and whether they are in force today, paginated (RF22, HU11, RNF14). */
public interface ListSuspensionsUseCase {

    PageResult<SuspensionDetails> list(ListSuspensionsQuery query);
}
