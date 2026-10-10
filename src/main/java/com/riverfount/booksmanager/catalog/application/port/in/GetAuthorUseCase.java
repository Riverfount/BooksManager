package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Author;
import java.util.Optional;

public interface GetAuthorUseCase {

    Optional<Author> findById(Long id);
}
