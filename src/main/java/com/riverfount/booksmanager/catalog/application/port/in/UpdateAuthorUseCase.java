package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Author;

public interface UpdateAuthorUseCase {

    Author update(UpdateAuthorCommand command);
}
