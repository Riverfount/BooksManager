package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.domain.Reader;

public interface ListReadersUseCase {

    PageResult<Reader> list(ListReadersQuery query);
}
