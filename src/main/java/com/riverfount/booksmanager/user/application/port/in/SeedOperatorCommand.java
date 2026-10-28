package com.riverfount.booksmanager.user.application.port.in;

/** Login and password of an operator to create at startup. The password is kept out of the string form. */
public record SeedOperatorCommand(String login, String password) {

    @Override
    public String toString() {
        return "SeedOperatorCommand[login=" + login + ", password=***]";
    }
}
