package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.PublicEndpoint;
import com.riverfount.booksmanager.user.application.port.in.AcceptReaderInvitationCommand;
import com.riverfount.booksmanager.user.application.port.in.AcceptReaderInvitationUseCase;
import com.riverfount.booksmanager.user.application.port.in.RequestReaderPasswordResetCommand;
import com.riverfount.booksmanager.user.application.port.in.RequestReaderPasswordResetUseCase;
import com.riverfount.booksmanager.user.application.port.in.ResetReaderPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetReaderPasswordUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public routes of the reader before they have a session (Phase 6, RF08): accepting an invitation, asking for a
 * password reset and choosing the new password. They live under {@code /api/v1/auth}, like the login, which the
 * security chain leaves open and where the {@code Authorization} header is ignored. What authorizes each one is the
 * single-use token of the link sent to the reader's e-mail.
 */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, renovação do token e logout (RF08).")
class ReaderAccessAuthController {

    private final AcceptReaderInvitationUseCase acceptUseCase;
    private final RequestReaderPasswordResetUseCase requestResetUseCase;
    private final ResetReaderPasswordUseCase resetUseCase;

    ReaderAccessAuthController(AcceptReaderInvitationUseCase acceptUseCase,
            RequestReaderPasswordResetUseCase requestResetUseCase, ResetReaderPasswordUseCase resetUseCase) {
        this.acceptUseCase = acceptUseCase;
        this.requestResetUseCase = requestResetUseCase;
        this.resetUseCase = resetUseCase;
    }

    @PostMapping("/accept-invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Aceitar o convite de acesso",
            description = "O leitor escolhe o login e a senha com o token do link do convite e passa a ter acesso. O "
                    + "convite vale uma vez só. Convite desconhecido, usado, expirado ou substituído devolve sempre "
                    + "a mesma resposta 422, sem dizer qual foi o motivo. Login já usado ou senha fora da política "
                    + "também devolvem 422, e nesses casos o convite continua valendo para uma nova tentativa. Nenhum "
                    + "token é devolvido: depois de aceitar, o leitor entra pelo login.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Acesso criado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (campos ausentes)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Convite inválido ou expirado, login já usado ou senha fora da política",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        acceptUseCase.accept(new AcceptReaderInvitationCommand(request.token(), request.login(), request.password()));
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Pedir a redefinição da senha",
            description = "Envia um link de uso único (válido por 1 hora, configurável) ao e-mail informado, se ele "
                    + "for de um leitor ativo com acesso. A resposta é sempre 204 e não diz se o e-mail existe ou se "
                    + "algo foi enviado. Um novo pedido substitui o link anterior.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pedido recebido"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (e-mail ausente)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        requestResetUseCase.requestReset(new RequestReaderPasswordResetCommand(request.email()));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Redefinir a senha com o link",
            description = "Troca a senha do leitor com o token do link de redefinição, que vale uma vez só. Revoga os "
                    + "refresh tokens do leitor e encerra um bloqueio de login por tentativas erradas. Link "
                    + "desconhecido, usado ou expirado devolve sempre a mesma resposta 422; senha fora da política "
                    + "também devolve 422 e o link continua valendo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha alterada"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (campos ausentes)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Link inválido ou expirado, ou senha fora da política",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void resetPassword(@Valid @RequestBody ResetReaderPasswordRequest request) {
        resetUseCase.reset(new ResetReaderPasswordCommand(request.token(), request.password()));
    }
}
