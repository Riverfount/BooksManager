package com.riverfount.booksmanager.user.application.port.in;

/**
 * Unblocks a reader manually (RF10, HU06). Idempotent; also allowed for an
 * inactive reader that was blocked before being deactivated.
 */
public interface UnblockReaderUseCase {

    void unblock(Long id);
}
