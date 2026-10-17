package com.riverfount.booksmanager.user.application.port.in;

/**
 * Password reset of a system user by an administrator (RF09, HU29). The password
 * is a secret: it is kept out of the string form, so it cannot reach a log by
 * accident.
 */
public record ResetUserPasswordCommand(Long id, String password) {

    @Override
    public String toString() {
        return "ResetUserPasswordCommand[id=" + id + ", password=***]";
    }
}
