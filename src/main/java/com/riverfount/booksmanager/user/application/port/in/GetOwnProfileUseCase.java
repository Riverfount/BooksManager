package com.riverfount.booksmanager.user.application.port.in;

/** The reader reads their own registration (Phase 6, RF32). */
public interface GetOwnProfileUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reader does not exist
     */
    OwnProfile get(Long readerId);
}
