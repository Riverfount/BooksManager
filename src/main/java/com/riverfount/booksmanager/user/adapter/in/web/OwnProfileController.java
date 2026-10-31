package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.CurrentReader;
import com.riverfount.booksmanager.shared.adapter.in.web.ReaderOnly;
import com.riverfount.booksmanager.user.application.port.in.ChangeOwnPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ChangeOwnPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetOwnProfileUseCase;
import com.riverfount.booksmanager.user.application.port.in.UpdateOwnProfileCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateOwnProfileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader maintains their own registration (Phase 6, RF32, RN22): always the reader of the token ({@link
 * CurrentReader}), never one sent in the request. Only the contact data and the password are editable; the identity
 * data stays with the staff, and is not even part of the request.
 */
@ReaderOnly
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Reader profile", description = "O próprio cadastro do leitor: consultar, alterar telefone, e-mail e "
        + "responsável, e trocar a senha (RF32).")
class OwnProfileController {

    private final GetOwnProfileUseCase getUseCase;
    private final UpdateOwnProfileUseCase updateUseCase;
    private final ChangeOwnPasswordUseCase passwordUseCase;

    OwnProfileController(GetOwnProfileUseCase getUseCase, UpdateOwnProfileUseCase updateUseCase,
            ChangeOwnPasswordUseCase passwordUseCase) {
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
        this.passwordUseCase = passwordUseCase;
    }

    @GetMapping("/profile")
    @Operation(summary = "Consultar o meu cadastro",
            description = "Devolve o cadastro do leitor logado: nome, documento mascarado, e-mail, telefone, data de "
                    + "nascimento, o responsável (se houver) e se o leitor é menor de 18 anos hoje. Só traz o que é "
                    + "do próprio leitor. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O cadastro do leitor"),
            @ApiResponse(responseCode = "404", description = "O leitor do token não existe",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    OwnProfileResponse get(@CurrentReader Long readerId) {
        return OwnProfileResponse.from(getUseCase.get(readerId));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Alterar o meu cadastro",
            description = "Altera só os campos enviados (os omitidos mantêm o valor atual): telefone, e-mail e, para "
                    + "o leitor menor de 18 anos, o responsável. Trocar o e-mail exige a senha atual "
                    + "(`currentPassword`). Nome, documento, data de nascimento, bloqueio e situação só a equipe "
                    + "altera: não fazem parte do contrato e são ignorados se enviados. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "O cadastro do leitor, já alterado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "O leitor do token não existe",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Regra de negócio violada: dado inválido, responsável de leitor maior de 18 anos ou "
                            + "menor sem responsável, leitor inativo, senha atual ausente ou errada (a errada conta "
                            + "para o bloqueio da conta) ou conta bloqueada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    OwnProfileResponse update(@CurrentReader Long readerId, @Valid @RequestBody UpdateOwnProfileRequest request) {
        var guardian = request.guardian();
        return OwnProfileResponse.from(updateUseCase.update(new UpdateOwnProfileCommand(readerId, request.phone(),
                request.email(), guardian == null ? null : guardian.name(),
                guardian == null ? null : guardian.email(), guardian == null ? null : guardian.phone(),
                request.currentPassword())));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Trocar a minha senha",
            description = "Troca a senha do leitor logado. Exige a senha atual: se estiver errada a troca é recusada e "
                    + "a tentativa conta para o bloqueio da conta. A nova senha segue a política de senha. Depois da "
                    + "troca todas as sessões do leitor são encerradas e ele entra de novo com a nova senha. Só para "
                    + "o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha trocada; todas as sessões foram encerradas"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "O leitor do token não tem acesso ativo",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Senha atual errada, conta bloqueada por tentativas erradas, leitor inativo ou nova "
                            + "senha fora da política (diz o que falta)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void changePassword(@CurrentReader Long readerId, @Valid @RequestBody ChangeOwnPasswordRequest request) {
        passwordUseCase.change(new ChangeOwnPasswordCommand(readerId, request.currentPassword(),
                request.newPassword()));
    }
}
