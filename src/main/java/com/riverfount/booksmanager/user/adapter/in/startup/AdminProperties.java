package com.riverfount.booksmanager.user.adapter.in.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login and password of the initial administrator (RF08), read from the
 * {@code app.admin.login} and {@code app.admin.password} properties
 * (environment variables {@code APP_ADMIN_LOGIN} and {@code APP_ADMIN_PASSWORD}),
 * and the optional new password that recovers the access of an administrator who
 * forgot it ({@code app.admin.reset-password}, {@code APP_ADMIN_RESET_PASSWORD}),
 * which acts on the administrator given in the login. All are empty by default:
 * nothing is hardcoded, and no password sits in a migration or a versioned file
 * (RNF10). The passwords stay out of the string form so they cannot reach a log by
 * accident.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String login, String password, String resetPassword) {

    @Override
    public String toString() {
        return "AdminProperties[login=" + login + ", password=***, resetPassword=***]";
    }
}
