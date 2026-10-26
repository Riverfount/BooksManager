package com.riverfount.booksmanager.user.application.port.in;

/** Takes the access away from a reader (Phase 6, RF08, RF27). */
public interface RevokeReaderAccessUseCase {

    /**
     * Deactivates the active access of the reader, revokes its refresh tokens and invalidates the pending links.
     * Idempotent: a reader with no active access changes nothing.
     *
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reader does not exist
     */
    void revoke(Long readerId);
}
