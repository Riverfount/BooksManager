package com.riverfount.booksmanager.user.application.port.in;

/** "I forgot my password": the e-mail the reader is registered with. */
public record RequestReaderPasswordResetCommand(String email) {

    /** Without the e-mail, which is personal data, so the string form is safe to log. */
    @Override
    public String toString() {
        return "RequestReaderPasswordResetCommand[email=***]";
    }
}
