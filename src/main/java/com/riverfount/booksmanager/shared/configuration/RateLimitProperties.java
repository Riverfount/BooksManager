package com.riverfount.booksmanager.shared.configuration;

import com.riverfount.booksmanager.shared.application.port.out.RateLimitPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The limits of attempts (Phase 6, RNF31), from the {@code app.rate-limit.*} properties: how many requests a reader may
 * make to the routes of the reader in a window, and how many password reset requests an e-mail may make. A missing or
 * invalid value fails at startup instead of silently turning the protection off.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Limit readerRoutes, Limit passwordReset) {

    public RateLimitProperties {
        if (readerRoutes == null || passwordReset == null) {
            throw new IllegalStateException("app.rate-limit.reader-routes e app.rate-limit.password-reset são obrigatórios");
        }
    }

    /** How many attempts in how long. */
    public record Limit(int maxAttempts, Duration window) {

        public Limit {
            if (maxAttempts < 1) {
                throw new IllegalStateException("o limite de tentativas (max-attempts) deve ser ao menos 1");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalStateException("a janela (window) deve ser uma duração positiva (por exemplo, 1m)");
            }
        }
    }

    /** The requests of a reader to {@code /api/v1/me/**}, counted per reader. */
    public RateLimitPolicy readerRoutesPolicy() {
        return new RateLimitPolicy("reader-routes", readerRoutes.maxAttempts(), readerRoutes.window());
    }

    /** The "I forgot my password" requests, counted per e-mail. */
    public RateLimitPolicy passwordResetPolicy() {
        return new RateLimitPolicy("password-reset", passwordReset.maxAttempts(), passwordReset.window());
    }
}
