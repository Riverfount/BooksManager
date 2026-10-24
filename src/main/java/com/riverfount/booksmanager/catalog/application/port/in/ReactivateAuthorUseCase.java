package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Author;

/** Reactivates an inactivated author (RF02, RN14); reactivating an active author is a no-op. */
public interface ReactivateAuthorUseCase {

    Author reactivate(Long id);
}
