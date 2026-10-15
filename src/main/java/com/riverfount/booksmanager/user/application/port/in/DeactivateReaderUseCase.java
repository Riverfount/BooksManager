package com.riverfount.booksmanager.user.application.port.in;

/**
 * Deactivates a reader (RF07, HU27). Readers are never physically deleted
 * (RN14); deactivating an inactive reader is a no-op. A blocked reader can
 * be deactivated and stays blocked.
 */
public interface DeactivateReaderUseCase {

    void deactivate(Long id);
}
