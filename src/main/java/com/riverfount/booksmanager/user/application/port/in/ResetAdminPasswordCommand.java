package com.riverfount.booksmanager.user.application.port.in;

/**
 * The administrator whose access is recovered, and the new password, both read from the environment at startup
 * (RF08, RF09). The password stays out of the string form so it cannot reach a log by accident.
 */
public record ResetAdminPasswordCommand(String login, String password) {

    @Override
    public String toString() {
        return "ResetAdminPasswordCommand[login=***, password=***]";
    }
}
