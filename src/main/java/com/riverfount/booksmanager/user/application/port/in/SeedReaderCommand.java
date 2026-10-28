package com.riverfount.booksmanager.user.application.port.in;

import java.time.LocalDate;

/**
 * A reader with its access to create at startup: the registration data (RF07) and the login and password of the
 * READER user. The password is kept out of the string form.
 */
public record SeedReaderCommand(String login, String password, String name, String document, String email,
        LocalDate birthDate) {

    @Override
    public String toString() {
        return "SeedReaderCommand[login=" + login + ", password=***, name=" + name + "]";
    }
}
