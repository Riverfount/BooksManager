package com.riverfount.booksmanager.user.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login lockout settings (RF08), from the {@code app.login-lockout.*}
 * properties: how many wrong passwords block a user and for how long. A
 * missing or invalid value fails at startup instead of silently turning the
 * protection off.
 */
@ConfigurationProperties(prefix = "app.login-lockout")
public record LoginLockoutProperties(int maxFailedAttempts, Duration blockDuration) {

    public LoginLockoutProperties {
        if (maxFailedAttempts < 1) {
            throw new IllegalStateException("app.login-lockout.max-failed-attempts deve ser ao menos 1");
        }
        if (blockDuration == null || blockDuration.isZero() || blockDuration.isNegative()) {
            throw new IllegalStateException(
                    "app.login-lockout.block-duration deve ser uma duração positiva (por exemplo, 15m)");
        }
    }
}
