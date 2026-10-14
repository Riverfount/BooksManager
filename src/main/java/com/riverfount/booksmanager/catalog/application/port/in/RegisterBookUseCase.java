package com.riverfount.booksmanager.catalog.application.port.in;

public interface RegisterBookUseCase {

    BookWithAvailability register(RegisterBookCommand command);
}
