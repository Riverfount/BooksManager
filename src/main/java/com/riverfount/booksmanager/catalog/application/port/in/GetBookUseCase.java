package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Book;
import java.util.Optional;

public interface GetBookUseCase {

    Optional<Book> findById(Long id);
}
