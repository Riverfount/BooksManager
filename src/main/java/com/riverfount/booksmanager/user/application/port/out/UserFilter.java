package com.riverfount.booksmanager.user.application.port.out;

import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;

/**
 * Optional filters of the user listing (RF09); a null field means "no
 * filter on that field". The login matches by "contains", ignoring case (it
 * is normalized like the registration); a blank login counts as absent. The role is the one exact profile to list;
 * without it every profile is listed, the accesses of the readers (Phase 6) included.
 */
public record UserFilter(String login, Boolean active, Role role) {

    public UserFilter {
        var normalizedLogin = User.normalizeLogin(login);
        login = normalizedLogin.isEmpty() ? null : normalizedLogin;
    }

    public UserFilter(String login, Boolean active) {
        this(login, active, null);
    }

    public static UserFilter none() {
        return new UserFilter(null, null, null);
    }
}
