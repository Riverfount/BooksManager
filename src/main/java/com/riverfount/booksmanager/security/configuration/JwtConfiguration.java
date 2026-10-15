package com.riverfount.booksmanager.security.configuration;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The JWT encoder and decoder (RF08, RNF08): HS256 with one shared secret
 * from the environment (RNF10). The decoder checks the signature, the
 * issuer and the expiry with no clock tolerance, and accepts only HS256 (so
 * neither {@code alg: none} nor another algorithm passes). The application
 * does not start without a strong enough secret.
 *
 * <p>Both live here so that the tests can issue tokens; the login (issue
 * #103) uses the encoder to issue the real ones.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
public class JwtConfiguration {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        var decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    private static SecretKey secretKey(JwtProperties properties) {
        var secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("APP_JWT_SECRET não foi informado: defina a variável de ambiente "
                    + "(com ao menos " + MIN_SECRET_BYTES + " bytes) para a aplicação assinar e validar os "
                    + "tokens JWT.");
        }
        var bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("APP_JWT_SECRET deve ter ao menos " + MIN_SECRET_BYTES
                    + " bytes (256 bits) para o HS256, mas o valor informado tem " + bytes.length + " bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
