package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

/** The books most borrowed in a period, from the most to the least, paginated (RF24, RNF14). */
public interface ListMostBorrowedBooksUseCase {

    PageResult<MostBorrowedBook> list(ListMostBorrowedBooksQuery query);
}
