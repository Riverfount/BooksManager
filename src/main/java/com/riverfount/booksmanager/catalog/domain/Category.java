package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;

/**
 * Category of the collection (RF03). Books with loan history are
 * deactivated, never physically deleted (RN14).
 */
public class Category {

    public static final int MAX_NAME_LENGTH = 100;

    private final Long id;
    private String name;
    private boolean active;

    private Category(Long id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    public static Category create(String name) {
        validateName(name);
        return new Category(null, TitleCase.normalize(name), true);
    }

    public static Category reconstitute(Long id, String name, boolean active) {
        return new Category(id, name, active);
    }

    /**
     * Decides whether the name can be used, given whether another category
     * already has it. Whoever fetches that fact (the application, via the
     * repository) does not decide; it only brings the fact for the domain
     * to decide.
     */
    public static void validateUniqueName(String name, boolean alreadyExistsWithThisName) {
        if (alreadyExistsWithThisName) {
            throw new BusinessRuleException("já existe uma categoria com o nome '" + name + "'");
        }
    }

    public void rename(String newName) {
        validateName(newName);
        this.name = TitleCase.normalize(newName);
    }

    public void deactivate() {
        this.active = false;
    }

    /** Brings an inactivated category back (RN14); reactivating an active one changes nothing. */
    public void reactivate() {
        this.active = true;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("o nome da categoria é obrigatório");
        }
        if (!name.equals(name.strip())) {
            throw new BusinessRuleException("o nome da categoria não pode ter espaços nas pontas");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException(
                    "o nome da categoria deve ter até " + MAX_NAME_LENGTH + " caracteres");
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
