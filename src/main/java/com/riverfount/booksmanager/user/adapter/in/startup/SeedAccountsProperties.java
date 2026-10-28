package com.riverfount.booksmanager.user.adapter.in.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional operator and reader created at startup, read from the {@code app.seed.operator.*} and
 * {@code app.seed.reader.*} properties (environment variables {@code APP_OPERATOR_*} and {@code APP_READER_*}).
 * Everything is empty by default: nothing is hardcoded, and the passwords stay out of the string forms so they
 * cannot reach a log by accident.
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedAccountsProperties(Operator operator, Reader reader) {

    public SeedAccountsProperties {
        operator = operator != null ? operator : new Operator(null, null);
        reader = reader != null ? reader : new Reader(null, null, null, null, null, null);
    }

    public record Operator(String login, String password) {

        @Override
        public String toString() {
            return "Operator[login=" + login + ", password=***]";
        }
    }

    /** {@code birthDate} is ISO-8601 (yyyy-MM-dd). */
    public record Reader(String login, String password, String name, String document, String email,
            String birthDate) {

        @Override
        public String toString() {
            return "Reader[login=" + login + ", password=***, name=" + name + "]";
        }
    }
}
