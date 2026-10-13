package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Partial update of a category (RF03, HU19): a null field means "keep the
 * current value".
 */
public record UpdateCategoryCommand(Long id, String name) {
}
