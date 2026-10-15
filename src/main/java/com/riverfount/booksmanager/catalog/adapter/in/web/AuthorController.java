package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.DeactivateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/authors")
@Tag(name = "Authors", description = "Cadastro, consulta, listagem, edição e inativação de autores do acervo (RF02).")
class AuthorController {

    private final RegisterAuthorUseCase registerAuthorUseCase;
    private final GetAuthorUseCase getAuthorUseCase;
    private final ListAuthorsUseCase listAuthorsUseCase;
    private final UpdateAuthorUseCase updateAuthorUseCase;
    private final DeactivateAuthorUseCase deactivateAuthorUseCase;

    AuthorController(RegisterAuthorUseCase registerAuthorUseCase, GetAuthorUseCase getAuthorUseCase,
            ListAuthorsUseCase listAuthorsUseCase, UpdateAuthorUseCase updateAuthorUseCase,
            DeactivateAuthorUseCase deactivateAuthorUseCase) {
        this.registerAuthorUseCase = registerAuthorUseCase;
        this.getAuthorUseCase = getAuthorUseCase;
        this.listAuthorsUseCase = listAuthorsUseCase;
        this.updateAuthorUseCase = updateAuthorUseCase;
        this.deactivateAuthorUseCase = deactivateAuthorUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar autor", description = "Cadastra um novo autor do acervo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Autor cadastrado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<AuthorResponse> register(@Valid @RequestBody RegisterAuthorRequest request) {
        var author = registerAuthorUseCase.register(new RegisterAuthorCommand(request.name()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(author.getId())
                .toUri();
        return ResponseEntity.created(location).body(AuthorResponse.from(author));
    }

    @GetMapping
    @Operation(summary = "Listar autores",
            description = "Lista os autores de forma paginada (máximo de 100 por página), com filtro opcional "
                    + "por nome. Por padrão, só os ativos e em ordem de nome.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de autores"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<AuthorResponse> list(
            @Parameter(description = "Trecho do nome, sem diferenciar maiúsculas", example = "assis")
            @RequestParam(required = false) String name,
            @Parameter(description = "Listar ativos (true) ou inativos (false)")
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, name",
                    example = "name,asc")
            @RequestParam(defaultValue = ListAuthorsQuery.DEFAULT_SORT) String sort) {
        var query = new ListAuthorsQuery(name, active, PageQuery.of(page, size, sort));
        return PageResponse.from(listAuthorsUseCase.list(query), AuthorResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar autor por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autor encontrado"),
            @ApiResponse(responseCode = "404", description = "Autor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    AuthorResponse findById(@PathVariable Long id) {
        return getAuthorUseCase.findById(id)
                .map(AuthorResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("autor não encontrado: " + id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar autor",
            description = "Altera só os campos enviados; os omitidos mantêm o valor atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autor atualizado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Autor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    AuthorResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAuthorRequest request) {
        var author = updateAuthorUseCase.update(new UpdateAuthorCommand(id, request.name()));
        return AuthorResponse.from(author);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativar autor",
            description = "Inativa o autor, sem excluí-lo fisicamente (RN14). Os livros já cadastrados mantêm a "
                    + "associação, mas o autor inativo não pode ser usado em livros novos. Inativar um autor já "
                    + "inativo não tem efeito.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Autor inativado"),
            @ApiResponse(responseCode = "404", description = "Autor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void deactivate(@PathVariable Long id) {
        deactivateAuthorUseCase.deactivate(id);
    }
}
