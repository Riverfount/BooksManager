package com.riverfount.booksmanager.user.application.port.in;

/**
 * Login and password to authenticate (RF08, HU05). The password is a secret:
 * it is kept out of the string form, so it cannot reach a log by accident.
 */
public record AuthenticateUserCommand(String login, String password) {

    @Override
    public String toString() {
        return "AuthenticateUserCommand[login=" + login + ", password=***]";
    }
}
