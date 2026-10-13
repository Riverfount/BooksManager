package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Deactivates an author (RF02, HU18). Authors are never physically
 * deleted (RN14); deactivating an inactive author is a no-op.
 */
public interface DeactivateAuthorUseCase {

    void deactivate(Long id);
}
