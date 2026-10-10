package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryUseCase;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Cadastro e consulta de categorias do acervo (RF03).")
class CategoryController {

    private final RegisterCategoryUseCase registerCategoryUseCase;
    private final GetCategoryUseCase getCategoryUseCase;

    CategoryController(RegisterCategoryUseCase registerCategoryUseCase,
            GetCategoryUseCase getCategoryUseCase) {
        this.registerCategoryUseCase = registerCategoryUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
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
}
