package com.riverfount.booksmanager.user.application.port.in;

/**
 * The password change of the administrator or operator of the token (RF09). The user comes from the token, never from
 * the path or the body. The passwords stay out of the string form so they cannot reach a log by accident.
 */
public record ChangeStaffPasswordCommand(Long userId, String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "ChangeStaffPasswordCommand[userId=" + userId + ", currentPassword=***, newPassword=***]";
    }
}
