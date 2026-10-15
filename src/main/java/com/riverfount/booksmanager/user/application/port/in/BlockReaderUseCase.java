package com.riverfount.booksmanager.user.application.port.in;

/**
 * Blocks a reader manually (RF10, HU06). Idempotent; an inactive reader
 * cannot be blocked. No reason is stored: the audit trail (RF27) will cover it.
 */
public interface BlockReaderUseCase {

    void block(Long id);
}
