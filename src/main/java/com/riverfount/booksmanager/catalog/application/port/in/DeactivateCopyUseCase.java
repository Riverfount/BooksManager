package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Deactivates a copy (RF04). Copies are never physically deleted (RN14);
 * a borrowed copy cannot be deactivated (RN10), and deactivating an
 * inactive copy is a no-op.
 */
public interface DeactivateCopyUseCase {

    void deactivate(Long id);
}
