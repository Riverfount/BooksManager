package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyUseCase;
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
@RequestMapping("/api/v1/copies")
@Tag(name = "Copies", description = "Cadastro e consulta de exemplares de um livro (RF04).")
class CopyController {

    private final RegisterCopyUseCase registerCopyUseCase;
    private final GetCopyUseCase getCopyUseCase;

    CopyController(RegisterCopyUseCase registerCopyUseCase, GetCopyUseCase getCopyUseCase) {
        this.registerCopyUseCase = registerCopyUseCase;
        this.getCopyUseCase = getCopyUseCase;
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
