package com.riverfount.booksmanager.user.application.port.in;

/**
 * What a reader changes about themself (RF32): a null field keeps the current value. The reader is the one of the
 * token, never one sent in the request. The current password is only needed (and only checked) when the e-mail changes.
 */
public record UpdateOwnProfileCommand(Long readerId, String phone, String email, String guardianName,
        String guardianEmail, String guardianPhone, String currentPassword) {

    /** Without the personal data and the password, so the string form is safe to log. */
    @Override
    public String toString() {
        return "UpdateOwnProfileCommand[readerId=" + readerId + ", ...]";
    }
}
