package com.riverfount.booksmanager.security.adapter.out.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** The access token carries who the user is, for how long, and is checked by the application's own decoder. */
class JwtAccessTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final JwtProperties PROPERTIES = new JwtProperties(
            "a-secret-with-more-than-thirty-two-bytes-in-it", "booksmanager", Duration.ofMinutes(15));

    private static JwtEncoder encoderFor(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    }

    /** Checks the signature only: the tokens of these tests are dated in a fixed past, so the real decoder would call them expired. */
    private final NimbusJwtDecoder signatureOnlyDecoder = signatureOnlyDecoder();

    private static NimbusJwtDecoder signatureOnlyDecoder() {
        var decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(PROPERTIES.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return decoder;
    }
    private final JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(encoderFor(PROPERTIES), PROPERTIES);

    @Test
    void shouldIssueATokenWithTheSubjectTheRoleTheIssuerAndTheDates() {
        var issued = issuer.issue(new AuthenticatedUser(42L, Role.ADMIN), NOW);

        var claims = signatureOnlyDecoder.decode(issued.value());
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(claims.getClaimAsString("iss")).isEqualTo("booksmanager");
        assertThat(claims.getIssuedAt()).isEqualTo(NOW);
        assertThat(claims.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void shouldReportTheSameExpiryAsTheTokenCarries() {
        var issued = issuer.issue(new AuthenticatedUser(42L, Role.OPERATOR), NOW);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void shouldUseTheConfiguredLifetime() {
        var properties = new JwtProperties(PROPERTIES.secret(), "booksmanager", Duration.ofMinutes(5));
        var shortLived = new JwtAccessTokenIssuer(encoderFor(properties), properties);

        assertThat(shortLived.issue(new AuthenticatedUser(1L, Role.OPERATOR), NOW).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void shouldCarryNoPersonalDataOrSecret() {
        var issued = issuer.issue(new AuthenticatedUser(42L, Role.ADMIN), NOW);

        var claims = signatureOnlyDecoder.decode(issued.value()).getClaims();
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "role", "iss", "iat", "exp");
    }

    @Test
    void theIssuedTokenShouldNotShowItsValueInTheStringForm() {
        var issued = issuer.issue(new AuthenticatedUser(42L, Role.ADMIN), NOW);

        assertThat(issued.toString()).doesNotContain(issued.value());
    }
}
