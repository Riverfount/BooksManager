package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Deactivates a book (RF01). Books are never physically deleted (RN14);
 * deactivating an inactive book is a no-op.
 */
public interface DeactivateBookUseCase {

    void deactivate(Long id);
}
