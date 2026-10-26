package com.riverfount.booksmanager.user.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reader access settings (Phase 6), from the {@code app.reader-access.*} properties: how long the invitation and the
 * password reset links stay valid, and the address of the front-end pages they point to, as templates where
 * {@code {token}} is replaced by the token. A missing or invalid value fails at startup.
 */
@ConfigurationProperties(prefix = "app.reader-access")
public record ReaderAccessProperties(Duration invitationValidity, Duration passwordResetValidity,
        String invitationUrl, String passwordResetUrl) {

    private static final String PLACEHOLDER = "{token}";

    public ReaderAccessProperties {
        requirePositive(invitationValidity, "invitation-validity");
        requirePositive(passwordResetValidity, "password-reset-validity");
        requireTemplate(invitationUrl, "invitation-url");
        requireTemplate(passwordResetUrl, "password-reset-url");
    }

    public String invitationLink(String token) {
        return invitationUrl.replace(PLACEHOLDER, token);
    }

    public String passwordResetLink(String token) {
        return passwordResetUrl.replace(PLACEHOLDER, token);
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException("app.reader-access." + name + " deve ser uma duração positiva (por exemplo, 72h)");
        }
    }

    private static void requireTemplate(String url, String name) {
        if (url == null || !url.contains(PLACEHOLDER)) {
            throw new IllegalStateException("app.reader-access." + name + " deve conter " + PLACEHOLDER);
        }
    }
}
