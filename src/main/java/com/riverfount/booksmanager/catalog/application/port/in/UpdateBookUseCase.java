package com.riverfount.booksmanager.catalog.application.port.in;

public interface UpdateBookUseCase {

    BookWithAvailability update(UpdateBookCommand command);
}
