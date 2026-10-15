package com.riverfount.booksmanager.security.configuration;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings (RF08), from the {@code app.auth.*} properties: how
 * long the refresh token lives and the attributes of the {@code httpOnly}
 * cookie that carries it. Combinations that browsers would reject fail at
 * startup instead of silently breaking the login.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Duration refreshTokenTtl, RefreshCookie refreshCookie) {

    public AuthProperties {
        if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalStateException("app.auth.refresh-token-ttl deve ser uma duração positiva (por exemplo, 7d)");
        }
        if (refreshCookie == null) {
            throw new IllegalStateException("app.auth.refresh-cookie não foi configurado");
        }
    }

    /**
     * The refresh token cookie. {@code sameSite} is Strict, Lax or None (any case);
     * {@code None} is only valid with {@code secure}, as browsers drop it otherwise.
     */
    public record RefreshCookie(String name, boolean secure, String sameSite, String path) {

        private static final Set<String> SAME_SITE_VALUES = Set.of("strict", "lax", "none");

        public RefreshCookie {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("app.auth.refresh-cookie.name é obrigatório");
            }
            if (path == null || path.isBlank()) {
                throw new IllegalStateException("app.auth.refresh-cookie.path é obrigatório");
            }
            if (sameSite == null || !SAME_SITE_VALUES.contains(sameSite.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalStateException("app.auth.refresh-cookie.same-site deve ser Strict, Lax ou None");
            }
            if (sameSite.equalsIgnoreCase("none") && !secure) {
                throw new IllegalStateException(
                        "app.auth.refresh-cookie.same-site=None exige app.auth.refresh-cookie.secure=true");
            }
        }
    }
}
