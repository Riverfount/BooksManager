package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageResult;

public interface ListAuthorsUseCase {

    PageResult<Author> list(ListAuthorsQuery query);
}
