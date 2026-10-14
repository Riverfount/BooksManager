package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCopiesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCopiesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyUseCase;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/copies")
@Tag(name = "Copies", description = "Cadastro, consulta e listagem de exemplares de um livro (RF04).")
class CopyController {

    private final RegisterCopyUseCase registerCopyUseCase;
    private final GetCopyUseCase getCopyUseCase;
    private final ListCopiesUseCase listCopiesUseCase;

    CopyController(RegisterCopyUseCase registerCopyUseCase, GetCopyUseCase getCopyUseCase,
            ListCopiesUseCase listCopiesUseCase) {
        this.registerCopyUseCase = registerCopyUseCase;
        this.getCopyUseCase = getCopyUseCase;
        this.listCopiesUseCase = listCopiesUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar exemplar",
            description = "Cadastra um novo exemplar de um livro já cadastrado e ativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Exemplar cadastrado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Código de patrimônio já cadastrado (RN02), ou livro inexistente ou "
                            + "inativo",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<CopyResponse> register(@Valid @RequestBody RegisterCopyRequest request) {
        var copy = registerCopyUseCase.register(
                new RegisterCopyCommand(request.bookId(), request.assetCode()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(copy.getId())
                .toUri();
        return ResponseEntity.created(location).body(CopyResponse.from(copy));
    }

    @GetMapping
    @Operation(summary = "Listar exemplares",
            description = "Lista os exemplares de forma paginada (máximo de 100 por página), com filtros "
                    + "opcionais por livro e por status. Por padrão, só os ativos e em ordem de código de "
                    + "patrimônio. Tamanho de página acima de 100 é rejeitado com 400, não ajustado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de exemplares"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<CopyResponse> list(
            @Parameter(description = "Id do livro, para listar só os exemplares dele", example = "1")
            @RequestParam(required = false) Long bookId,
            @Parameter(description = "Status do exemplar",
                    schema = @Schema(allowableValues = {"AVAILABLE", "BORROWED", "RESERVED", "DAMAGED",
                            "LOST"}))
            @RequestParam(required = false) CopyStatus status,
            @Parameter(description = "Listar ativos (true) ou inativos (false)")
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, assetCode, status",
                    example = "assetCode,asc")
            @RequestParam(defaultValue = ListCopiesQuery.DEFAULT_SORT) String sort) {
        var query = new ListCopiesQuery(bookId, status, active, PageQuery.of(page, size, sort));
        return PageResponse.from(listCopiesUseCase.list(query), CopyResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar exemplar por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exemplar encontrado"),
            @ApiResponse(responseCode = "404", description = "Exemplar não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    CopyResponse findById(@PathVariable Long id) {
        return getCopyUseCase.findById(id)
                .map(CopyResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("exemplar não encontrado: " + id));
    }
}
