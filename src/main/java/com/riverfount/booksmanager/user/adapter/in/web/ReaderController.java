package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.GetReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderUseCase;
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
@RequestMapping("/api/v1/readers")
@Tag(name = "Readers", description = "Cadastro, consulta, listagem, edição, inativação e bloqueio de leitores (RF07, RF10).")
class ReaderController {

    private final RegisterReaderUseCase registerReaderUseCase;
    private final GetReaderUseCase getReaderUseCase;

    ReaderController(RegisterReaderUseCase registerReaderUseCase, GetReaderUseCase getReaderUseCase) {
        this.registerReaderUseCase = registerReaderUseCase;
        this.getReaderUseCase = getReaderUseCase;
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
}
