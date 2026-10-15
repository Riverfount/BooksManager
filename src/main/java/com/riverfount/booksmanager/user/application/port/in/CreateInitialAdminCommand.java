package com.riverfount.booksmanager.user.application.port.in;

/**
 * Login and password of the first administrator (RF08, RNF10), read from the
 * environment. The password is a secret: it is kept out of the string form,
 * so it cannot reach a log by accident.
 */
public record CreateInitialAdminCommand(String login, String password) {

    @Override
    public String toString() {
        return "CreateInitialAdminCommand[login=" + login + ", password=***]";
    }
}
