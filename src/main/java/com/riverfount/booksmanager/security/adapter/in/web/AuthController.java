package com.riverfount.booksmanager.security.adapter.in.web;

import com.riverfount.booksmanager.security.application.port.in.LoginCommand;
import com.riverfount.booksmanager.security.application.port.in.TokenPair;
import com.riverfount.booksmanager.security.application.port.in.LoginUseCase;
import com.riverfount.booksmanager.security.configuration.AuthProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login e emissão dos tokens de acesso (RF08).")
class AuthController {

    private final LoginUseCase loginUseCase;
    private final AuthProperties.RefreshCookie refreshCookie;

    AuthController(LoginUseCase loginUseCase, AuthProperties properties) {
        this.loginUseCase = loginUseCase;
        this.refreshCookie = properties.refreshCookie();
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Entrar",
            description = "Autentica por login e senha. Devolve o access token (JWT) no corpo e define o refresh "
                    + "token (opaco) em um cookie `httpOnly`, que o JavaScript da página não consegue ler; o "
                    + "cookie só é enviado às rotas de autenticação. O front-end deve fazer a requisição com "
                    + "credenciais (`credentials: 'include'`). Login inexistente, senha errada e usuário inativo "
                    + "devolvem a mesma resposta 401, sem dizer qual foi o motivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado: access token no corpo e refresh "
                    + "token no cookie `Set-Cookie`"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (login ou senha ausentes)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Login ou senha inválidos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = loginUseCase.login(new LoginCommand(request.login(), request.password()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieOf(result).toString())
                .body(new LoginResponse(result.accessToken(), LoginResponse.BEARER,
                        result.accessTokenExpiresInSeconds()));
    }

    private ResponseCookie refreshCookieOf(TokenPair result) {
        return ResponseCookie.from(refreshCookie.name(), result.refreshToken())
                .httpOnly(true)
                .secure(refreshCookie.secure())
                .sameSite(refreshCookie.sameSite())
                .path(refreshCookie.path())
                .maxAge(result.refreshTokenLifetime())
                .build();
    }
}
