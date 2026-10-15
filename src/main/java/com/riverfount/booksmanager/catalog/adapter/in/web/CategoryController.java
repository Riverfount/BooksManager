package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.DeactivateCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Cadastro, consulta, listagem, edição e inativação de categorias do acervo (RF03).")
class CategoryController {

    private final RegisterCategoryUseCase registerCategoryUseCase;
    private final GetCategoryUseCase getCategoryUseCase;
    private final ListCategoriesUseCase listCategoriesUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final DeactivateCategoryUseCase deactivateCategoryUseCase;

    CategoryController(RegisterCategoryUseCase registerCategoryUseCase,
            GetCategoryUseCase getCategoryUseCase, ListCategoriesUseCase listCategoriesUseCase,
            UpdateCategoryUseCase updateCategoryUseCase, DeactivateCategoryUseCase deactivateCategoryUseCase) {
        this.registerCategoryUseCase = registerCategoryUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
        this.listCategoriesUseCase = listCategoriesUseCase;
        this.updateCategoryUseCase = updateCategoryUseCase;
        this.deactivateCategoryUseCase = deactivateCategoryUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar categoria", description = "Cadastra uma nova categoria do acervo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria cadastrada"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Já existe uma categoria com esse nome",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<CategoryResponse> register(@Valid @RequestBody RegisterCategoryRequest request) {
        var category = registerCategoryUseCase.register(new RegisterCategoryCommand(request.name()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(category.getId())
                .toUri();
        return ResponseEntity.created(location).body(CategoryResponse.from(category));
    }

    @GetMapping
    @Operation(summary = "Listar categorias",
            description = "Lista as categorias de forma paginada (máximo de 100 por página), com filtro "
                    + "opcional por nome. Por padrão, só as ativas e em ordem de nome.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de categorias"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<CategoryResponse> list(
            @Parameter(description = "Trecho do nome, sem diferenciar maiúsculas", example = "ficção")
            @RequestParam(required = false) String name,
            @Parameter(description = "Listar ativas (true) ou inativas (false)")
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, name",
                    example = "name,asc")
            @RequestParam(defaultValue = ListCategoriesQuery.DEFAULT_SORT) String sort) {
        var query = new ListCategoriesQuery(name, active, PageQuery.of(page, size, sort));
        return PageResponse.from(listCategoriesUseCase.list(query), CategoryResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar categoria por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    CategoryResponse findById(@PathVariable Long id) {
        return getCategoryUseCase.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("categoria não encontrada: " + id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar categoria",
            description = "Altera só os campos enviados; os omitidos mantêm o valor atual. O nome continua "
                    + "único: manter o próprio nome atual não é considerado repetição.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Já existe outra categoria com esse nome",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    CategoryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        var category = updateCategoryUseCase.update(new UpdateCategoryCommand(id, request.name()));
        return CategoryResponse.from(category);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativar categoria",
            description = "Inativa a categoria, sem excluí-la fisicamente (RN14). Os livros já cadastrados "
                    + "continuam com ela, mas a categoria inativa não pode ser usada em livros novos. Inativar "
                    + "uma categoria já inativa não tem efeito.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoria inativada"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void deactivate(@PathVariable Long id) {
        deactivateCategoryUseCase.deactivate(id);
    }
}
