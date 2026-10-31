package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.user.application.port.in.ChangeStaffPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ChangeStaffPasswordUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The administrator or operator changes their own password (RF09), next to {@code GET /users/me}. The user is the one
 * of the token and never comes from the path or the body. The reader changes the password in {@code /me/password}.
 */
@AdminOrOperator
@RestController
@RequestMapping("/api/v1/users/me/password")
@Tag(name = "Current user", description = "Os dados de quem está logado, para qualquer perfil.")
class StaffPasswordController {

    private final ChangeStaffPasswordUseCase changeStaffPasswordUseCase;

    StaffPasswordController(ChangeStaffPasswordUseCase changeStaffPasswordUseCase) {
        this.changeStaffPasswordUseCase = changeStaffPasswordUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Trocar a minha senha (administrador e operador)",
            description = "Troca a senha de quem está logado. Exige a senha atual: se estiver errada a troca é recusada "
                    + "e a tentativa conta para o bloqueio da conta, e enquanto a conta estiver bloqueada nada é "
                    + "conferido. A nova senha segue a política de senha (de 8 a 25 caracteres, com maiúscula, "
                    + "minúscula, número e caractere especial). Depois da troca todas as sessões do usuário são "
                    + "encerradas e ele entra de novo com a nova senha; o access token já emitido vale até expirar "
                    + "(no máximo 15 minutos). Só para administrador e operador: o leitor troca a senha em "
                    + "`POST /api/v1/me/password`. A troca fica registrada na auditoria, sem nenhuma senha.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha trocada; todas as sessões foram encerradas"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "O usuário do token não existe",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Senha atual errada, conta bloqueada por tentativas erradas, usuário inativo ou nova "
                            + "senha fora da política (diz o que falta)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void changePassword(@AuthenticationPrincipal Jwt caller, @Valid @RequestBody ChangeOwnPasswordRequest request) {
        changeStaffPasswordUseCase.change(new ChangeStaffPasswordCommand(CurrentUserController.idOf(caller),
                request.currentPassword(), request.newPassword()));
    }
}
