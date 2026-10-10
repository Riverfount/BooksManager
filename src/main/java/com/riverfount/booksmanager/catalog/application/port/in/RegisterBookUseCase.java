package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Book;

public interface RegisterBookUseCase {

    Book register(RegisterBookCommand command);
}
