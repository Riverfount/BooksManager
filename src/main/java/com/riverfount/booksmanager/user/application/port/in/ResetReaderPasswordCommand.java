package com.riverfount.booksmanager.user.application.port.in;

/** The new password of a reader, chosen with the token of the reset link. */
public record ResetReaderPasswordCommand(String token, String password) {

    @Override
    public String toString() {
        return "ResetReaderPasswordCommand[token=***, password=***]";
    }
}
