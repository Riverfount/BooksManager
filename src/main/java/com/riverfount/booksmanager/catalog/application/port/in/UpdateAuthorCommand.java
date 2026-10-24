package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Partial update of an author (RF02, HU16): a null field means "keep the current value". {@code allowDuplicate}
 * confirms that the new name is shared on purpose with another active author.
 */
public record UpdateAuthorCommand(Long id, String name, boolean allowDuplicate) {

    /** An update that does not confirm a homonym. */
    public UpdateAuthorCommand(Long id, String name) {
        this(id, name, false);
    }
}
