package com.riverfount.booksmanager.user.application.port.out;

import com.riverfount.booksmanager.user.domain.User;

/**
 * Optional filters of the user listing (RF09); a null field means "no
 * filter on that field". The login matches by "contains", ignoring case (it
 * is normalized like the registration); a blank login counts as absent.
 */
public record UserFilter(String login, Boolean active) {

    public UserFilter {
        var normalizedLogin = User.normalizeLogin(login);
        login = normalizedLogin.isEmpty() ? null : normalizedLogin;
    }

    public static UserFilter none() {
        return new UserFilter(null, null);
    }
}
