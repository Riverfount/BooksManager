package com.riverfount.booksmanager.security.application.port.out;

import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import java.time.Instant;

/**
 * Outbound port that issues the access token of a user (RF08). The adapter
 * decides the format (JWT) and the lifetime.
 */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(AuthenticatedUser user, Instant now);
}
