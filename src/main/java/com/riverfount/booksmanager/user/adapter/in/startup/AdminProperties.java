package com.riverfount.booksmanager.user.adapter.in.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login and password of the initial administrator (RF08), read from the
 * {@code app.admin.login} and {@code app.admin.password} properties
 * (environment variables {@code APP_ADMIN_LOGIN} and {@code APP_ADMIN_PASSWORD}).
 * Both are empty by default: nothing is hardcoded, and the password never
 * sits in a migration or a versioned file (RNF10). The password stays out of
 * the string form so it cannot reach a log by accident.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String login, String password) {

    @Override
    public String toString() {
        return "AdminProperties[login=" + login + ", password=***]";
    }
}
