package com.riverfount.booksmanager.security.adapter.in.web;

import com.riverfount.booksmanager.security.application.port.in.InvalidRefreshTokenException;
import com.riverfount.booksmanager.security.application.port.in.LoginCommand;
import com.riverfount.booksmanager.security.application.port.in.LoginUseCase;
import com.riverfount.booksmanager.security.application.port.in.LogoutCommand;
import com.riverfount.booksmanager.security.application.port.in.LogoutUseCase;
import com.riverfount.booksmanager.security.application.port.in.RefreshCommand;
import com.riverfount.booksmanager.security.application.port.in.RefreshTokenUseCase;
import com.riverfount.booksmanager.security.application.port.in.TokenPair;
import com.riverfount.booksmanager.security.configuration.AuthProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, renovação do token e logout (RF08).")
class AuthController {

    /**
     * The refresh and logout routes act on a cookie the browser sends by itself, so a page of
     * another site could try to trigger them. A custom header cannot be sent across sites without
     * the CORS check, which only lets the front-end origin through, so requiring it closes that
     * door (on top of the cookie being SameSite=Strict).
     */
    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_HEADER_VALUE = "XMLHttpRequest";

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final AuthProperties.RefreshCookie refreshCookie;

    AuthController(LoginUseCase loginUseCase, RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase, AuthProperties properties) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
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
        return tokensResponse(loginUseCase.login(new LoginCommand(request.login(), request.password())));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Renovar o token",
            description = "Troca o refresh token (lido do cookie `httpOnly`) por um novo access token e por um novo "
                    + "refresh token, que vem no cookie: cada refresh token vale uma vez só (rotação). Usar um token "
                    + "que já foi trocado ou revogado revoga todos os tokens do usuário. Token ausente, "
                    + "desconhecido, expirado, revogado ou de usuário inativo devolve sempre a mesma resposta 401, "
                    + "que também limpa o cookie. Exige o cabeçalho `X-Requested-With: XMLHttpRequest` (proteção "
                    + "contra CSRF) e a requisição com credenciais (`credentials: 'include'`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Renovado: novo access token no corpo e novo "
                    + "refresh token no cookie `Set-Cookie`"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado ou revogado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Falta o cabeçalho `X-Requested-With: XMLHttpRequest`",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<LoginResponse> refresh(HttpServletRequest request,
            @Parameter(description = "Proteção contra CSRF: deve valer `XMLHttpRequest`", required = true,
                    example = CSRF_HEADER_VALUE)
            @RequestHeader(name = CSRF_HEADER, required = false) String requestedWith) {
        requireCsrfHeader(requestedWith);
        var tokens = refreshTokenUseCase.refresh(new RefreshCommand(refreshTokenIn(request)));
        return tokensResponse(tokens);
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Sair",
            description = "Revoga o refresh token do cookie (só esta sessão; as de outros dispositivos continuam) "
                    + "e limpa o cookie. Sempre devolve 204, inclusive sem cookie ou com um token desconhecido ou "
                    + "já revogado, e não diz se o token existia. O access token já emitido continua válido até "
                    + "expirar (no máximo 15 minutos). Exige o cabeçalho `X-Requested-With: XMLHttpRequest`.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sessão encerrada; o cookie é limpo"),
            @ApiResponse(responseCode = "403", description = "Falta o cabeçalho `X-Requested-With: XMLHttpRequest`",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<Void> logout(HttpServletRequest request,
            @Parameter(description = "Proteção contra CSRF: deve valer `XMLHttpRequest`", required = true,
                    example = CSRF_HEADER_VALUE)
            @RequestHeader(name = CSRF_HEADER, required = false) String requestedWith) {
        requireCsrfHeader(requestedWith);
        logoutUseCase.logout(new LogoutCommand(refreshTokenIn(request)));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString()).build();
    }

    /** A refused refresh also tells the browser to drop the cookie, so it stops sending a useless token. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ProblemDetail> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Refresh token inválido, expirado ou revogado.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .body(problem);
    }

    @ExceptionHandler(MissingCsrfHeaderException.class)
    ResponseEntity<ProblemDetail> handleMissingCsrfHeader(MissingCsrfHeaderException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Esta rota exige o cabeçalho " + CSRF_HEADER + ": " + CSRF_HEADER_VALUE + ".");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    private static void requireCsrfHeader(String requestedWith) {
        if (requestedWith == null || !requestedWith.equalsIgnoreCase(CSRF_HEADER_VALUE)) {
            throw new MissingCsrfHeaderException();
        }
    }

    /** The value of the refresh token cookie, or null when the browser did not send it. */
    private String refreshTokenIn(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(refreshCookie.name())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseEntity<LoginResponse> tokensResponse(TokenPair tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieOf(tokens).toString())
                .body(new LoginResponse(tokens.accessToken(), LoginResponse.BEARER,
                        tokens.accessTokenExpiresInSeconds()));
    }

    private ResponseCookie refreshCookieOf(TokenPair result) {
        return baseCookie(result.refreshToken()).maxAge(result.refreshTokenLifetime()).build();
    }

    /** Same name, path and attributes with an empty value and no life: the browser deletes the cookie. */
    private ResponseCookie clearedRefreshCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(refreshCookie.name(), value)
                .httpOnly(true)
                .secure(refreshCookie.secure())
                .sameSite(refreshCookie.sameSite())
                .path(refreshCookie.path());
    }

    private static final class MissingCsrfHeaderException extends RuntimeException {
        MissingCsrfHeaderException() {
            super("missing " + CSRF_HEADER + " header", null, false, false);
        }
    }
}
