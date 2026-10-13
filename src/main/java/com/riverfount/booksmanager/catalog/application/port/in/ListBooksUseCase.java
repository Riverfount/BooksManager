package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.shared.domain.PageResult;

public interface ListBooksUseCase {

    PageResult<Book> list(ListBooksQuery query);
}
