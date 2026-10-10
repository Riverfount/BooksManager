package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Author;

public interface RegisterAuthorUseCase {

    Author register(RegisterAuthorCommand command);
}
