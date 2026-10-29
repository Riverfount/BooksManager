package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.AnyProfile;
import com.riverfount.booksmanager.user.application.port.in.GetUserUseCase;
import com.riverfount.booksmanager.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who is logged in. It lives under {@code /users/me}, not under {@code /auth}: the authentication
 * routes are public and ignore the {@code Authorization} header, so a route there would never
 * see the token. The literal path wins over {@code /users/{id}}, which is for administrators.
 */
@AnyProfile
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Current user", description = "Os dados de quem está logado, para qualquer perfil.")
class CurrentUserController {

    private final GetUserUseCase getUserUseCase;

    CurrentUserController(GetUserUseCase getUserUseCase) {
        this.getUserUseCase = getUserUseCase;
    }

    @GetMapping
    @Operation(summary = "Consultar o usuário logado",
            description = "Devolve o id, o login, o nome de exibição (nulo quando não há; use o login) e o perfil "
                    + "de quem fez a requisição, lido do token. Vale para todos os perfis (administrador, operador e leitor). Usuário "
                    + "inativado ou inexistente recebe 401, mesmo com um token ainda dentro da validade. Nunca "
                    + "traz a senha nem o hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário logado"),
            @ApiResponse(responseCode = "401", description = "Sem token válido, ou usuário inativo ou inexistente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    CurrentUserResponse me(@AuthenticationPrincipal Jwt caller) {
        return getUserUseCase.findById(idOf(caller))
                .filter(User::isActive)
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("unknown or inactive user"));
    }

    /** The id the token carries as its subject; without it nobody can be told apart, so the request is refused. */
    private static Long idOf(Jwt caller) {
        if (caller == null || caller.getSubject() == null) {
            throw new AccessDeniedException("unknown caller");
        }
        try {
            return Long.valueOf(caller.getSubject());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("unknown caller");
        }
    }
}
