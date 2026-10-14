package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.Optional;

public interface GetBookUseCase {

    Optional<BookWithAvailability> findById(Long id);
}
