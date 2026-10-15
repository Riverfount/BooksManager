package com.riverfount.booksmanager;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Builds access tokens for the tests: valid ones with the application's own
 * encoder, and defective ones (expired, from another issuer, signed with
 * another key or algorithm, unsigned) to check that the chain refuses them.
 */
public final class TestTokens {

    private static final String ROLE_CLAIM = "role";

    private TestTokens() {
    }

    /** A valid token of the user, for 15 minutes. */
    public static String valid(JwtEncoder encoder, JwtProperties properties, String role) {
        return signed(encoder, properties.issuer(), "1", role, Instant.now(), Instant.now().plus(Duration.ofMinutes(15)));
    }

    /** A token that expired the given time ago. */
    public static String expired(JwtEncoder encoder, JwtProperties properties, Duration expiredFor) {
        var expiresAt = Instant.now().minus(expiredFor);
        return signed(encoder, properties.issuer(), "1", "ADMIN", expiresAt.minus(Duration.ofMinutes(15)), expiresAt);
    }

    public static String fromIssuer(JwtEncoder encoder, String issuer, String role) {
        return signed(encoder, issuer, "1", role, Instant.now(), Instant.now().plus(Duration.ofMinutes(15)));
    }

    public static String signed(JwtEncoder encoder, String issuer, String subject, String role, Instant issuedAt,
            Instant expiresAt) {
        var claims = JwtClaimsSet.builder().subject(subject).issuedAt(issuedAt).expiresAt(expiresAt);
        if (issuer != null) {
            claims.issuer(issuer);
        }
        if (role != null) {
            claims.claim(ROLE_CLAIM, role);
        }
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /** A well-formed token signed with another secret. */
    public static String signedWithAnotherKey(JwtProperties properties) {
        return signWith(JWSAlgorithm.HS256, "another-secret-another-secret-another-secret!!", properties);
    }

    /** A token signed with the right secret but with HS512, which the application does not accept. */
    public static String signedWithAnotherAlgorithm(String secret, JwtProperties properties) {
        return signWith(JWSAlgorithm.HS512, secret, properties);
    }

    /** A token whose header says {@code alg: none}, with no signature. */
    public static String unsigned(JwtProperties properties) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        var payload = encoder.encodeToString(("{\"iss\":\"" + properties.issuer() + "\",\"sub\":\"1\",\"role\":\"ADMIN\","
                + "\"exp\":" + Instant.now().plus(Duration.ofMinutes(15)).getEpochSecond() + "}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".";
    }

    /** A valid token with its payload swapped for another one, so the signature no longer matches. */
    public static String tampered(String validToken) {
        var parts = validToken.split("\\.");
        var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"role\":\"OPERATOR\"", "\"role\":\"ADMIN\"");
        if (payload.equals(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))) {
            payload = payload.replace("\"sub\":\"1\"", "\"sub\":\"2\"");
        }
        var changed = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + changed + "." + parts[2];
    }

    private static String signWith(JWSAlgorithm algorithm, String secret, JwtProperties properties) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .issuer(properties.issuer())
                    .subject("1")
                    .claim(ROLE_CLAIM, "ADMIN")
                    .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                    .build();
            var jwt = new SignedJWT(new JWSHeader(algorithm), claims);
            // the HS512 signer needs a key of at least 64 bytes
            var keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (algorithm.equals(JWSAlgorithm.HS512) && keyBytes.length < 64) {
                var padded = new byte[64];
                System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
                keyBytes = padded;
            }
            jwt.sign(new MACSigner(keyBytes));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
