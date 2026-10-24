package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;

/**
 * Author of the collection (RF02). Authors with loan history are
 * deactivated, never physically deleted (RN14).
 */
public class Author {

    public static final int MAX_NAME_LENGTH = 150;

    private final Long id;
    private String name;
    private boolean active;

    private Author(Long id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    public static Author create(String name) {
        validateName(name);
        return new Author(null, name, true);
    }

    public static Author reconstitute(Long id, String name, boolean active) {
        return new Author(id, name, active);
    }

    public void rename(String newName) {
        validateName(newName);
        this.name = newName;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Brings an inactivated author back (RN14); reactivating an active one changes nothing. */
    public void reactivate() {
        this.active = true;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("o nome do autor é obrigatório");
        }
        if (!name.equals(name.strip())) {
            throw new BusinessRuleException("o nome do autor não pode ter espaços nas pontas");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException(
                    "o nome do autor deve ter até " + MAX_NAME_LENGTH + " caracteres");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
