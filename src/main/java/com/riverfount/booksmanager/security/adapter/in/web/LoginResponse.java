package com.riverfount.booksmanager.security.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The body of a successful login: only the access token. The refresh token
 * never appears here; it travels in an httpOnly cookie, out of reach of the
 * page's scripts.
 */
record LoginResponse(
        @Schema(requiredMode = REQUIRED, description = "Access token JWT, para o cabeçalho "
                + "`Authorization: Bearer <token>`")
        String accessToken,
        @Schema(requiredMode = REQUIRED, description = "Tipo do token", example = "Bearer")
        String tokenType,
        @Schema(requiredMode = REQUIRED, description = "Validade do access token, em segundos", example = "900")
        long expiresIn) {

    static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "LoginResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
