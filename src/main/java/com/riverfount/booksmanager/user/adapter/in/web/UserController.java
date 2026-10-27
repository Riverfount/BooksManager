package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.DeactivateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.DeactivateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.ListUsersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListUsersUseCase;
import com.riverfount.booksmanager.user.application.port.in.ResetUserPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetUserPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.in.UnblockUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserUseCase;
import com.riverfount.booksmanager.user.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@AdminOnly
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Gestão dos usuários do sistema, só para administradores (RF09).")
class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final UnblockUserUseCase unblockUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final Clock clock;

    UserController(CreateUserUseCase createUserUseCase, GetUserUseCase getUserUseCase,
            ListUsersUseCase listUsersUseCase, UpdateUserUseCase updateUserUseCase,
            UnblockUserUseCase unblockUserUseCase, DeactivateUserUseCase deactivateUserUseCase,
            ResetUserPasswordUseCase resetUserPasswordUseCase, Clock clock) {
        this.createUserUseCase = createUserUseCase;
        this.getUserUseCase = getUserUseCase;
        this.listUsersUseCase = listUsersUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.unblockUserUseCase = unblockUserUseCase;
        this.deactivateUserUseCase = deactivateUserUseCase;
        this.resetUserPasswordUseCase = resetUserPasswordUseCase;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "Criar usuário",
            description = "Cria um usuário do sistema, só para administrador, com nome de exibição opcional. O login "
                    + "é único (sem diferenciar maiúsculas) e a senha precisa cumprir a política: de 8 a 25 "
                    + "caracteres, com maiúscula, "
                    + "minúscula, número e caractere especial. A senha é guardada só como hash e a resposta nunca "
                    + "a traz.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Login repetido ou inválido, ou senha que não cumpre a política (a mensagem "
                            + "diz quais regras faltam)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        var user = createUserUseCase.create(new CreateUserCommand(request.login(), request.password(),
                request.role(), request.displayName()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user, Instant.now(clock)));
    }

    @GetMapping
    @Operation(summary = "Listar usuários",
            description = "Lista os usuários do sistema de forma paginada (máximo de 100 por página), só para "
                    + "administrador, com filtro opcional por trecho do login (sem diferenciar maiúsculas) e por "
                    + "perfil. Sem o perfil, só os usuários que operam o sistema (ADMIN e OPERATOR): os acessos dos "
                    + "leitores são geridos pelo cadastro do leitor e aparecem com role=READER. Por "
                    + "padrão, só os ativos e em ordem de login. Cada item mostra o perfil e se o usuário está "
                    + "bloqueado agora por tentativas de login; nunca a senha nem o hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuários"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<UserResponse> list(
            @Parameter(description = "Trecho do login, sem diferenciar maiúsculas", example = "mar")
            @RequestParam(required = false) String login,
            @Parameter(description = "Listar ativos (true) ou inativos (false)")
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Listar só este perfil (ADMIN, OPERATOR ou READER). Sem ele, a lista traz os "
                    + "usuários que operam o sistema (ADMIN e OPERATOR), sem os acessos dos leitores",
                    example = "READER")
            @RequestParam(required = false) Role role,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, login",
                    example = "login,asc")
            @RequestParam(defaultValue = ListUsersQuery.DEFAULT_SORT) String sort) {
        var query = new ListUsersQuery(login, active, role, PageQuery.of(page, size, sort));
        var now = Instant.now(clock);
        return PageResponse.from(listUsersUseCase.list(query), user -> UserResponse.from(user, now));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar usuário por id",
            description = "Devolve o usuário, ativo ou não, com o perfil e o estado do bloqueio por tentativas de "
                    + "login. Só para administrador. Nunca traz a senha nem o hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    UserResponse findById(@PathVariable Long id) {
        return getUserUseCase.findById(id)
                .map(user -> UserResponse.from(user, Instant.now(clock)))
                .orElseThrow(() -> new ResourceNotFoundException("usuário não encontrado: " + id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar o usuário",
            description = "Altera o perfil (`ADMIN` ou `OPERATOR`) e o nome de exibição do usuário, só para "
                    + "administrador; um nome de exibição em branco o remove. Um campo "
                    + "omitido ou nulo mantém o valor atual. O login não muda, a senha tem rota própria e a "
                    + "inativação também; esses campos, se vierem no corpo, são ignorados. Não é possível tirar o "
                    + "perfil de administrador do último administrador ativo (422). O perfil novo vale no próximo "
                    + "login e na próxima renovação do token; o access token já emitido mantém o perfil antigo até "
                    + "expirar (no máximo 15 minutos).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário atualizado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (perfil desconhecido, por exemplo)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "O último administrador ativo não pode deixar de ser "
                    + "administrador",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        var user = updateUserUseCase.update(new UpdateUserCommand(id, request.role(), request.displayName()));
        return UserResponse.from(user, Instant.now(clock));
    }

    @PostMapping("/{id}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desbloquear usuário",
            description = "Encerra na hora o bloqueio por tentativas de login (3 senhas erradas seguidas) e zera "
                    + "a contagem de senhas erradas: a senha certa volta a entrar em seguida. Só para "
                    + "administrador. Desbloquear quem não está bloqueado não tem efeito. Um administrador "
                    + "bloqueado só pode ser desbloqueado por outro administrador; se for o único, o bloqueio "
                    + "termina sozinho em até 15 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário desbloqueado"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void unblock(@PathVariable Long id) {
        unblockUserUseCase.unblock(id);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Redefinir a senha do usuário",
            description = "Define uma nova senha para o usuário, só para administrador. A senha nova segue a mesma "
                    + "política da criação (de 8 a 25 caracteres, com maiúscula, minúscula, número e caractere "
                    + "especial), é guardada só como hash e nunca é devolvida nem registrada em log. Todos os "
                    + "refresh tokens do usuário são revogados: quem estava logado precisa entrar de novo. O access "
                    + "token já emitido continua valendo até expirar (no máximo 15 minutos). O usuário inativo "
                    + "continua inativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha redefinida"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (senha ausente, em branco ou grande demais)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Senha que não cumpre a política (a mensagem diz quais regras faltam)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetUserPasswordRequest request) {
        resetUserPasswordUseCase.resetPassword(new ResetUserPasswordCommand(id, request.password()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativar usuário",
            description = "Inativa o usuário, sem excluí-lo fisicamente (RN14): o registro fica, o usuário deixa "
                    + "de poder entrar ou renovar o token, e todos os seus refresh tokens são revogados. Só para "
                    + "administrador. Inativar quem já está inativo não tem efeito (204). Não é possível inativar "
                    + "a si mesmo nem o último administrador ativo (422). O access token que o usuário já recebeu "
                    + "continua valendo até expirar (no máximo 15 minutos), pois não há consulta ao banco a cada "
                    + "requisição.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário inativado"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "O próprio usuário ou o último administrador ativo não podem ser inativados",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void deactivate(@PathVariable Long id, @AuthenticationPrincipal Jwt caller) {
        deactivateUserUseCase.deactivate(new DeactivateUserCommand(id, idOf(caller)));
    }

    /**
     * The id of the user doing the request, which the token carries as its subject. Without
     * it the rule "nobody deactivates themselves" could not be applied, so the request is
     * refused (the chain only lets a token issued by the login through, so this does not
     * happen in practice).
     */
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
