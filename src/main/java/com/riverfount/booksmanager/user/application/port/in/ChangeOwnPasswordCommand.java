package com.riverfount.booksmanager.user.application.port.in;

/** The password change of a reader: the current one proves it is them, the new one follows the password policy. */
public record ChangeOwnPasswordCommand(Long readerId, String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "ChangeOwnPasswordCommand[readerId=" + readerId + ", currentPassword=***, newPassword=***]";
    }
}
