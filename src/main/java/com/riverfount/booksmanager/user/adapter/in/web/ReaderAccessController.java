package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.user.application.port.in.InviteReaderAccessCommand;
import com.riverfount.booksmanager.user.application.port.in.InviteReaderAccessUseCase;
import com.riverfount.booksmanager.user.application.port.in.RevokeReaderAccessUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gives and takes away the access of a reader to the system (Phase 6, RF08): the administrator and the operator
 * invite the reader, who then chooses a login and a password through the public routes of
 * {@link ReaderAccessAuthController}.
 */
@AdminOrOperator
@RestController
@RequestMapping("/api/v1/readers/{id}")
@Tag(name = "Reader access", description = "Convite e revogação do acesso do leitor ao sistema.")
class ReaderAccessController {

    private final InviteReaderAccessUseCase inviteUseCase;
    private final RevokeReaderAccessUseCase revokeUseCase;

    ReaderAccessController(InviteReaderAccessUseCase inviteUseCase, RevokeReaderAccessUseCase revokeUseCase) {
        this.inviteUseCase = inviteUseCase;
        this.revokeUseCase = revokeUseCase;
    }

    @PostMapping("/access-invitations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Convidar o leitor para acessar o sistema",
            description = "Envia ao e-mail do leitor um link de uso único para ele escolher o login e a senha (válido "
                    + "por 72 horas, configurável). Um novo convite substitui o anterior que ainda estava pendente. "
                    + "A resposta diz até quando o link vale e nunca traz o token. Para ADMIN e OPERATOR. O envio do "
                    + "e-mail é feito depois da gravação e uma falha dele não desfaz o convite. Leitor inexistente "
                    + "devolve 404; leitor inativo ou que já tem acesso devolve 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Convite enviado"),
            @ApiResponse(responseCode = "404", description = "Leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Leitor inativo ou que já tem acesso ao sistema",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderAccessInvitationResponse invite(@PathVariable Long id) {
        return ReaderAccessInvitationResponse.from(inviteUseCase.invite(new InviteReaderAccessCommand(id)));
    }

    @DeleteMapping("/access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revogar o acesso do leitor",
            description = "Inativa o acesso do leitor (o registro fica, RN14), revoga os seus refresh tokens e "
                    + "invalida os links pendentes (convite e redefinição de senha). O leitor continua cadastrado e "
                    + "pode ser convidado de novo. É idempotente: sem acesso ativo, devolve 204 sem mudar nada. O "
                    + "access token já emitido vale até expirar (no máximo 15 minutos). Para ADMIN e OPERATOR. "
                    + "Inativar o leitor também revoga o acesso.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Acesso revogado (ou o leitor não tinha acesso ativo)"),
            @ApiResponse(responseCode = "404", description = "Leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void revoke(@PathVariable Long id) {
        revokeUseCase.revoke(id);
    }
}
