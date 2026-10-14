package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.shared.domain.PageResult;

public interface ListCopiesUseCase {

    PageResult<Copy> list(ListCopiesQuery query);
}
