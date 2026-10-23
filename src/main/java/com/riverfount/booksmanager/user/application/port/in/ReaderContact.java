package com.riverfount.booksmanager.user.application.port.in;

/** How to address a reader in a message (RF28): the name and the e-mail, and nothing more of the personal data. */
public record ReaderContact(String name, String email) {
}
