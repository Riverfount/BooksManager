package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Deactivates a category (RF03, HU21). Categories are never physically
 * deleted (RN14); deactivating an inactive category is a no-op.
 */
public interface DeactivateCategoryUseCase {

    void deactivate(Long id);
}
