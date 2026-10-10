package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookUseCase;
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
@RequestMapping("/api/v1/books")
@Tag(name = "Books", description = "Cadastro e consulta de livros do acervo (RF01).")
class BookController {

    private final RegisterBookUseCase registerBookUseCase;
    private final GetBookUseCase getBookUseCase;

    BookController(RegisterBookUseCase registerBookUseCase, GetBookUseCase getBookUseCase) {
        this.registerBookUseCase = registerBookUseCase;
        this.getBookUseCase = getBookUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar livro",
            description = "Cadastra um novo livro. A categoria e os autores informados precisam existir e "
                    + "estar ativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Livro cadastrado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "ISBN inválido ou já cadastrado (RN01), ou categoria/autor inexistente "
                            + "ou inativo",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<BookResponse> register(@Valid @RequestBody RegisterBookRequest request) {
        var book = registerBookUseCase.register(new RegisterBookCommand(request.isbn(), request.title(),
                request.publisher(), request.publicationYear(), request.categoryId(), request.authorIds()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(book.getId())
                .toUri();
        return ResponseEntity.created(location).body(BookResponse.from(book));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar livro por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Livro encontrado"),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    BookResponse findById(@PathVariable Long id) {
        return getBookUseCase.findById(id)
                .map(BookResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("livro não encontrado: " + id));
    }
}
