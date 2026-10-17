package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Role;

/**
 * Data to create a system user (RF09, HU28). The password is a secret: it is
 * kept out of the string form, so it cannot reach a log by accident.
 */
public record CreateUserCommand(String login, String password, Role role, String displayName) {

    /** Without a display name (optional). */
    public CreateUserCommand(String login, String password, Role role) {
        this(login, password, role, null);
    }

    @Override
    public String toString() {
        return "CreateUserCommand[login=" + login + ", password=***, role=" + role + ", displayName="
                + displayName + "]";
    }
}
