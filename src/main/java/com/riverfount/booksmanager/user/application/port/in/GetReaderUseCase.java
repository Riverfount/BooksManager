package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Reader;
import java.util.Optional;

public interface GetReaderUseCase {

    /** Finds a reader by id, active or not (HU25). */
    Optional<Reader> findById(Long id);
}
