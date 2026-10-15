package com.riverfount.booksmanager.security.application.port.in;

/**
 * Login and password to log in (RF08, HU05). The password is a secret: it is
 * kept out of the string form, so it cannot reach a log by accident.
 */
public record LoginCommand(String login, String password) {

    @Override
    public String toString() {
        return "LoginCommand[login=" + login + ", password=***]";
    }
}
