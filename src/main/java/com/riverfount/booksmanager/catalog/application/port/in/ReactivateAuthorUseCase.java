package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Author;

/**
 * Reactivates an inactivated author (RF02, RN14); reactivating an active author is a no-op. Bringing back an author
 * whose name another active author has is refused like registering it would be (RN19), unless the homonym is
 * confirmed.
 */
public interface ReactivateAuthorUseCase {

    /** @param allowDuplicate confirms that the author is a homonym of an active one */
    Author reactivate(Long id, boolean allowDuplicate);
}
