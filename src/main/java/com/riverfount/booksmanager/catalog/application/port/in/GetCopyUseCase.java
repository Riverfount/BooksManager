package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Copy;
import java.util.Optional;

public interface GetCopyUseCase {

    Optional<Copy> findById(Long id);
}
