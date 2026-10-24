package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Registers an author (RF02). {@code allowDuplicate} confirms that the author is a homonym of an active one;
 * without it, a name that an active author already has is refused.
 */
public record RegisterAuthorCommand(String name, boolean allowDuplicate) {

    /** A registration that does not confirm a homonym. */
    public RegisterAuthorCommand(String name) {
        this(name, false);
    }
}
