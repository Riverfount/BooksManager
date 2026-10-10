package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
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
@RequestMapping("/api/v1/authors")
@Tag(name = "Authors", description = "Cadastro e consulta de autores do acervo (RF02).")
class AuthorController {

    private final RegisterAuthorUseCase registerAuthorUseCase;
    private final GetAuthorUseCase getAuthorUseCase;

    AuthorController(RegisterAuthorUseCase registerAuthorUseCase, GetAuthorUseCase getAuthorUseCase) {
        this.registerAuthorUseCase = registerAuthorUseCase;
        this.getAuthorUseCase = getAuthorUseCase;
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
}
