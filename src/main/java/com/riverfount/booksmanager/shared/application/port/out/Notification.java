package com.riverfount.booksmanager.shared.application.port.out;

/**
 * A message to one person (RF28): recipient address, subject and plain text. It carries only what the message
 * needs, nothing more about the reader (LGPD).
 */
public record Notification(String recipient, String subject, String text) {

    public Notification {
        requireText(recipient, "recipient");
        requireText(subject, "subject");
        requireText(text, "text");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification " + name + " must not be blank");
        }
    }
}
