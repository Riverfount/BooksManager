package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;

public interface ListBooksUseCase {

    PageResult<BookWithAvailability> list(ListBooksQuery query);
}
