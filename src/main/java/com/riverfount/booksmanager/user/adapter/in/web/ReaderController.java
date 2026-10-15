package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.BlockReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.DeactivateReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.ListReadersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListReadersUseCase;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.UnblockReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.UpdateReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateReaderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/readers")
@Tag(name = "Readers", description = "Cadastro, consulta, listagem, edição, inativação e bloqueio de leitores (RF07, RF10).")
class ReaderController {

    private final RegisterReaderUseCase registerReaderUseCase;
    private final GetReaderUseCase getReaderUseCase;
    private final ListReadersUseCase listReadersUseCase;
    private final UpdateReaderUseCase updateReaderUseCase;
    private final DeactivateReaderUseCase deactivateReaderUseCase;
    private final BlockReaderUseCase blockReaderUseCase;
    private final UnblockReaderUseCase unblockReaderUseCase;

    ReaderController(RegisterReaderUseCase registerReaderUseCase, GetReaderUseCase getReaderUseCase,
            ListReadersUseCase listReadersUseCase, UpdateReaderUseCase updateReaderUseCase,
            DeactivateReaderUseCase deactivateReaderUseCase, BlockReaderUseCase blockReaderUseCase,
            UnblockReaderUseCase unblockReaderUseCase) {
        this.registerReaderUseCase = registerReaderUseCase;
        this.getReaderUseCase = getReaderUseCase;
        this.listReadersUseCase = listReadersUseCase;
        this.updateReaderUseCase = updateReaderUseCase;
        this.deactivateReaderUseCase = deactivateReaderUseCase;
        this.blockReaderUseCase = blockReaderUseCase;
        this.unblockReaderUseCase = unblockReaderUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar leitor",
            description = "Cadastra um leitor com os dados pessoais mínimos (RNF11). O documento deve ser único "
                    + "depois de normalizado; o leitor menor de 18 anos exige o responsável, com nome e e-mail.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Leitor cadastrado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Regra de negócio violada: documento repetido, dados inválidos ou menor de "
                            + "18 anos sem o nome ou o e-mail do responsável",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<ReaderResponse> register(@Valid @RequestBody RegisterReaderRequest request) {
        var guardian = request.guardian();
        var reader = registerReaderUseCase.register(new RegisterReaderCommand(request.name(),
                request.document(), request.email(), request.phone(), request.birthDate(),
                guardian == null ? null : guardian.name(),
                guardian == null ? null : guardian.email(),
                guardian == null ? null : guardian.phone()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reader.getId())
                .toUri();
        return ResponseEntity.created(location).body(ReaderResponse.from(reader));
    }

    @GetMapping
    @Operation(summary = "Listar leitores",
            description = "Lista os leitores de forma paginada (máximo de 100 por página). O nome casa por trecho, "
                    + "sem diferenciar maiúsculas, e o documento por igualdade, depois de normalizado. Por padrão, "
                    + "só os ativos e em ordem de nome. Cada item traz o documento mascarado e não traz a data de "
                    + "nascimento nem o responsável; a consulta por id devolve tudo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de leitores"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<ReaderSummaryResponse> list(
            @Parameter(description = "Trecho do nome, sem diferenciar maiúsculas", example = "maria")
            @RequestParam(required = false) String name,
            @Parameter(description = "Documento completo (pontuação e espaços são ignorados)",
                    example = "123.456.789-00")
            @RequestParam(required = false) String document,
            @Parameter(description = "Listar ativos (true) ou inativos (false)")
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Só bloqueados (true) ou só não bloqueados (false); sem o parâmetro, ambos")
            @RequestParam(required = false) Boolean blocked,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, name",
                    example = "name,asc")
            @RequestParam(defaultValue = ListReadersQuery.DEFAULT_SORT) String sort) {
        var query = new ListReadersQuery(name, document, active, blocked, PageQuery.of(page, size, sort));
        return PageResponse.from(listReadersUseCase.list(query), ReaderSummaryResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar leitor por id",
            description = "Devolve o leitor com o documento completo, a data de nascimento, o responsável (quando "
                    + "houver) e a situação (`active` e `blocked`), inclusive para leitor inativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leitor encontrado"),
            @ApiResponse(responseCode = "404", description = "Leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderResponse findById(@PathVariable Long id) {
        return getReaderUseCase.findById(id)
                .map(ReaderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("leitor não encontrado: " + id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar leitor",
            description = "Altera só os campos enviados; os omitidos (ou nulos) mantêm o valor atual, então nenhum "
                    + "campo é apagado por esta rota. Só os dados pessoais são editáveis (nome, documento, e-mail, "
                    + "telefone, data de nascimento e responsável); a situação (`active`, `blocked`) tem rotas "
                    + "próprias e é ignorada se vier no corpo. O documento continua único (manter o próprio não é "
                    + "repetição) e o leitor menor de 18 anos precisa ficar com o responsável. Os campos enviados "
                    + "do responsável substituem os atuais, um a um.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leitor atualizado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Documento de outro leitor, dado inválido ou leitor menor de 18 anos sem o "
                            + "nome ou o e-mail do responsável",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderResponse update(@PathVariable Long id, @Valid @RequestBody UpdateReaderRequest request) {
        var guardian = request.guardian();
        var reader = updateReaderUseCase.update(new UpdateReaderCommand(id, request.name(), request.document(),
                request.email(), request.phone(), request.birthDate(),
                guardian == null ? null : guardian.name(),
                guardian == null ? null : guardian.email(),
                guardian == null ? null : guardian.phone()));
        return ReaderResponse.from(reader);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativar leitor",
            description = "Inativa o leitor, sem excluí-lo fisicamente (RN14): o registro e o histórico continuam, "
                    + "o leitor sai da listagem padrão e não pode fazer empréstimos nem reservas (RN11). Inativar "
                    + "um leitor já inativo não tem efeito. Um leitor bloqueado pode ser inativado e continua "
                    + "bloqueado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Leitor inativado"),
            @ApiResponse(responseCode = "404", description = "Leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void deactivate(@PathVariable Long id) {
        deactivateReaderUseCase.deactivate(id);
    }

    @PostMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bloquear leitor",
            description = "Bloqueia o leitor manualmente (RF10): enquanto estiver bloqueado, ele não pode fazer "
                    + "empréstimos nem reservas (RN11). Bloquear um leitor já bloqueado não tem efeito. Um leitor "
                    + "inativo não pode ser bloqueado. O motivo do bloqueio não é guardado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Leitor bloqueado"),
            @ApiResponse(responseCode = "404", description = "Leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Leitor inativo não pode ser bloqueado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void block(@PathVariable Long id) {
        blockReaderUseCase.block(id);
    }

    @PostMapping("/{id}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desbloquear leitor",
            description = "Desbloqueia o leitor manualmente (RF10). Desbloquear um leitor que não está bloqueado "
                    + "não tem efeito.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Leitor desbloqueado"),
            @ApiResponse(responseCode = "404", description = "Leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void unblock(@PathVariable Long id) {
        unblockReaderUseCase.unblock(id);
    }
}
