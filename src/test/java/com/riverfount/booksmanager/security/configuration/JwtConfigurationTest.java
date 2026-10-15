package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestTokens;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * The JWT beans (RF08, RNF08, RNF10): HS256 with the secret from the
 * environment, the issuer checked, no clock tolerance, and no way to start
 * without a strong enough secret.
 */
class JwtConfigurationTest {

    private static final String SECRET = "a-secret-with-more-than-thirty-two-bytes-in-it";
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, "booksmanager", java.time.Duration.ofMinutes(15));

    private final JwtConfiguration configuration = new JwtConfiguration();
    private final JwtEncoder encoder = configuration.jwtEncoder(PROPERTIES);
    private final JwtDecoder decoder = configuration.jwtDecoder(PROPERTIES);

    // the secret (RNF10)

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldNotBuildTheBeansWithoutTheSecret(String secret) {
        var properties = new JwtProperties(secret, "booksmanager", java.time.Duration.ofMinutes(15));

        assertThatThrownBy(() -> configuration.jwtDecoder(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET");
        assertThatThrownBy(() -> configuration.jwtEncoder(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET");
    }

    @Test
    void shouldRefuseASecretShorterThan32BytesWithoutRepeatingIt() {
        var weak = "only-31-bytes-long-secret-12345";
        assertThat(weak.getBytes()).hasSize(31);
        var properties = new JwtProperties(weak, "booksmanager", java.time.Duration.ofMinutes(15));

        assertThatThrownBy(() -> configuration.jwtDecoder(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(weak);
    }

    @Test
    void shouldAcceptASecretOfExactly32Bytes() {
        var properties = new JwtProperties("x".repeat(32), "booksmanager", java.time.Duration.ofMinutes(15));

        assertThat(configuration.jwtDecoder(properties)).isNotNull();
        assertThat(configuration.jwtEncoder(properties)).isNotNull();
    }

    @Test
    void theSecretShouldNotShowInTheStringFormOfTheProperties() {
        assertThat(PROPERTIES.toString()).doesNotContain(SECRET).contains("booksmanager");
    }

    // valid tokens

    @Test
    void shouldDecodeATokenIssuedByTheEncoder() {
        var token = TestTokens.valid(encoder, PROPERTIES, "OPERATOR");

        var jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("booksmanager");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OPERATOR");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    // refused tokens

    @Test
    void shouldRefuseAnExpiredToken() {
        var token = TestTokens.expired(encoder, PROPERTIES, Duration.ofMinutes(1));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldHaveNoClockToleranceEvenForATokenExpiredAFewSecondsAgo() {
        var token = TestTokens.expired(encoder, PROPERTIES, Duration.ofSeconds(5));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldRefuseATokenOfAnotherIssuer() {
        var token = TestTokens.fromIssuer(encoder, "someone-else", "ADMIN");

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldRefuseATokenWithoutIssuer() {
        var token = TestTokens.fromIssuer(encoder, null, "ADMIN");

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldRefuseATokenSignedWithAnotherKey() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.signedWithAnotherKey(PROPERTIES)))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldRefuseATokenWhosePayloadWasChanged() {
        var token = TestTokens.tampered(TestTokens.valid(encoder, PROPERTIES, "OPERATOR"));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldRefuseAnUnsignedToken() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.unsigned(PROPERTIES)))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldRefuseAnotherSigningAlgorithmEvenWithTheRightSecret() {
        assertThatThrownBy(() -> decoder.decode(TestTokens.signedWithAnotherAlgorithm(SECRET, PROPERTIES)))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void shouldRefuseSomethingThatIsNotAToken() {
        assertThatThrownBy(() -> decoder.decode("not-a-token")).isInstanceOf(BadJwtException.class);
    }
}
