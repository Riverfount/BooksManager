package com.riverfount.booksmanager.security.adapter.out.token;

import com.riverfount.booksmanager.security.application.port.out.AccessTokenIssuer;
import com.riverfount.booksmanager.security.application.port.out.IssuedAccessToken;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues the access token as a JWT signed with HS256 (RF08, RNF08). The token
 * carries only {@code sub} (the user id), {@code role}, {@code readerId}
 * (only for the access of a reader), {@code iss}, {@code iat} and {@code exp}: no login, no personal data, no secret. The
 * decoder in JwtConfiguration is the counterpart that validates it.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    /** The reader the access belongs to; only the access of a reader has it. */
    static final String READER_ID_CLAIM = "readerId";

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    JwtAccessTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(AuthenticatedUser user, Instant now) {
        var expiresAt = now.plus(properties.accessTokenTtl());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.id()))
                .claim("role", user.role().name())
                .issuedAt(now)
                .expiresAt(expiresAt);
        if (user.readerId() != null) {
            claims.claim(READER_ID_CLAIM, user.readerId());
        }
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedAccessToken(encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue(),
                expiresAt);
    }
}
