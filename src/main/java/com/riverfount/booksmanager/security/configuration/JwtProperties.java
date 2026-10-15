package com.riverfount.booksmanager.security.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the JWT access token (RF08, RNF10), from the {@code app.jwt.*}
 * properties. The {@code secret} comes from the environment variable
 * {@code APP_JWT_SECRET} and is empty by default: nothing is hardcoded, and
 * the application does not start without it. The access token lives for
 * {@code accessTokenTtl} (15 minutes by default). It is kept out of the string
 * form so it cannot reach a log by accident.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

    public JwtProperties {
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalStateException(
                    "app.jwt.access-token-ttl deve ser uma duração positiva (por exemplo, 15m)");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=***, issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "]";
    }
}
