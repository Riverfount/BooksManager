package com.riverfount.booksmanager.shared.adapter.out.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Mail server settings (RF28), read from {@code app.mail.*} (environment variables {@code APP_MAIL_HOST},
 * {@code APP_MAIL_PORT} and so on). Off by default (RNF10): while {@code enabled} is false nothing is sent, it is
 * only logged. The timeouts are short on purpose, so a server that does not answer cannot hold a request thread.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        @DefaultValue("false") boolean enabled,
        String host,
        @DefaultValue("587") int port,
        String username,
        String password,
        String from,
        @DefaultValue("true") boolean startTls,
        @DefaultValue("3s") Duration connectionTimeout,
        @DefaultValue("5s") Duration timeout) {

    public MailProperties {
        if (enabled && (host == null || host.isBlank())) {
            throw new IllegalArgumentException("app.mail.host (APP_MAIL_HOST) is required when the mail is enabled");
        }
        if (enabled && (from == null || from.isBlank())) {
            throw new IllegalArgumentException("app.mail.from (APP_MAIL_FROM) is required when the mail is enabled");
        }
    }

    boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    @Override
    public String toString() {
        return "MailProperties[enabled=" + enabled + ", host=" + host + ", port=" + port + "]";
    }
}
