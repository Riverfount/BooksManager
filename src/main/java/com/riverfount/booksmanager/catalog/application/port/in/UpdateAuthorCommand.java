package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Partial update of an author (RF02, HU16): a null field means "keep the
 * current value".
 */
public record UpdateAuthorCommand(Long id, String name) {
}
