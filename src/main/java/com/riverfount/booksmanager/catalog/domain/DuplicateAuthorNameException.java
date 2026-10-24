package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;

/**
 * The name is already taken by an active author (RF02). It is a business rule that the owner can override by
 * confirming that the new author is a homonym, so the exception carries the id of the author that already has the
 * name: the front shows who exists and asks "create another one anyway?".
 */
public class DuplicateAuthorNameException extends BusinessRuleException {

    private final Long existingAuthorId;

    public DuplicateAuthorNameException(String name, Long existingAuthorId) {
        super("já existe um autor ativo com o nome '" + name + "' (id " + existingAuthorId
                + "); se for outro autor com o mesmo nome, confirme com allowDuplicate=true");
        this.existingAuthorId = existingAuthorId;
    }

    public Long getExistingAuthorId() {
        return existingAuthorId;
    }
}
