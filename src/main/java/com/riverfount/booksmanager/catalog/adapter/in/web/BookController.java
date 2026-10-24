package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.DeactivateBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReactivateBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.LookupBookByIsbnUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
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
@RequestMapping("/api/v1/books")
@Tag(name = "Books", description = "Cadastro, consulta, listagem, edição e inativação de livros do acervo (RF01).")
class BookController {

    private final RegisterBookUseCase registerBookUseCase;
    private final GetBookUseCase getBookUseCase;
    private final ListBooksUseCase listBooksUseCase;
    private final UpdateBookUseCase updateBookUseCase;
    private final DeactivateBookUseCase deactivateBookUseCase;
    private final ReactivateBookUseCase reactivateBookUseCase;
    private final LookupBookByIsbnUseCase lookupBookByIsbnUseCase;

    BookController(RegisterBookUseCase registerBookUseCase, GetBookUseCase getBookUseCase,
            ListBooksUseCase listBooksUseCase, UpdateBookUseCase updateBookUseCase,
            DeactivateBookUseCase deactivateBookUseCase, ReactivateBookUseCase reactivateBookUseCase, LookupBookByIsbnUseCase lookupBookByIsbnUseCase) {
        this.registerBookUseCase = registerBookUseCase;
        this.getBookUseCase = getBookUseCase;
        this.listBooksUseCase = listBooksUseCase;
        this.updateBookUseCase = updateBookUseCase;
        this.deactivateBookUseCase = deactivateBookUseCase;
        this.reactivateBookUseCase = reactivateBookUseCase;
        this.lookupBookByIsbnUseCase = lookupBookByIsbnUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar livro",
            description = "Cadastra um novo livro. A categoria e os autores informados precisam existir e "
                    + "estar ativos. O ISBN-10 é guardado como o ISBN-13 do mesmo livro (a resposta traz o "
                    + "ISBN-13), então cadastrar um livro pelo ISBN-10 e outra vez pelo ISBN-13 é recusado "
                    + "como duplicado (RN01). A editora (até 255 caracteres) e o ano de publicação (de 1450 "
                    + "até o ano atual) são obrigatórios (RF01).")
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
                .buildAndExpand(book.book().getId())
                .toUri();
        return ResponseEntity.created(location).body(BookResponse.from(book));
    }

    @GetMapping
    @Operation(summary = "Listar e pesquisar livros",
            description = "Lista os livros de forma paginada (máximo de 100 por página). Por padrão, só os "
                    + "ativos e em ordem de título. Os filtros `title`, `author` e `isbn` casam por trecho, "
                    + "sem diferenciar maiúsculas (no ISBN, hífens e espaços são ignorados, e o ISBN é o "
                    + "ISBN-13 guardado), a `categoryId` "
                    + "casa pelo id, e os filtros informados valem em conjunto (RF05). Cada livro traz "
                    + "`availableCopies`, a quantidade de exemplares ativos e disponíveis. Tamanho de página "
                    + "acima de 100 é rejeitado com 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de livros"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<BookResponse> list(
            @Parameter(description = "Trecho do título", example = "casmurro")
            @RequestParam(required = false) String title,
            @Parameter(description = "Trecho do nome de um dos autores", example = "machado")
            @RequestParam(required = false) String author,
            @Parameter(description = "Trecho do ISBN-13 guardado, com ou sem hífens", example = "978-85-3330")
            @RequestParam(required = false) String isbn,
            @Parameter(description = "Id da categoria", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Listar ativos (true) ou inativos (false)")
            @RequestParam(defaultValue = "true") boolean active,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, title, isbn, "
                    + "publicationYear", example = "title,asc")
            @RequestParam(defaultValue = ListBooksQuery.DEFAULT_SORT) String sort) {
        var query = new ListBooksQuery(title, author, isbn, categoryId, active, PageQuery.of(page, size, sort));
        return PageResponse.from(listBooksUseCase.list(query), BookResponse::from);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Sugerir os dados de um livro pelo ISBN",
            description = "Consulta uma fonte externa pelo ISBN e devolve título, autores, editora e ano como "
                    + "sugestão para pré-preencher o cadastro (RF06); não grava nada, quem cadastra o livro é o "
                    + "`POST /api/v1/books`. Cada autor sugerido traz `existingAuthorId` quando o acervo já tem "
                    + "um autor ativo com esse nome (sem diferenciar maiúsculas), para não duplicar o cadastro. "
                    + "Se já existe um livro com o ISBN (o ISBN-10 e o ISBN-13 do mesmo livro são o mesmo "
                    + "ISBN), responde `ALREADY_REGISTERED` com o `registeredBookId` e não consulta a fonte. "
                    + "O `isbn` da resposta é sempre o ISBN-13. Fonte desligada, lenta, fora do ar ou sem o ISBN responde 200 com "
                    + "`NOT_FOUND` e uma mensagem: o cadastro manual segue funcionando (RNF19).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sugestão, ISBN já cadastrado ou nada encontrado"),
            @ApiResponse(responseCode = "400",
                    description = "ISBN ausente ou inválido (tamanho ou dígito verificador)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    BookLookupResponse lookup(
            @Parameter(description = "ISBN-10 ou ISBN-13, com ou sem hífens", example = "978-85-333-0227-3")
            @RequestParam(required = false) String isbn) {
        return BookLookupResponse.from(lookupBookByIsbnUseCase.lookup(isbn));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar livro por id",
            description = "Devolve o livro com o ISBN, o título, a editora, o ano, a categoria, os autores, se está "
                    + "ativo e a quantidade de exemplares disponíveis (`availableCopies`), inclusive para livro "
                    + "inativo.")
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

    @PatchMapping("/{id}")
    @Operation(summary = "Editar livro",
            description = "Altera só os campos enviados; os omitidos mantêm o valor atual. A editora e o ano podem "
                    + "ser trocados, mas não esvaziados (editora em branco é recusada); um livro anterior à "
                    + "exigência, sem editora ou ano, continua editável e pode ser completado. O ISBN é imutável "
                    + "(RN01): se vier no corpo, é ignorado. Categoria e autores só precisam estar ativos "
                    + "quando são novos para o livro; os que ele já tinha continuam aceitos mesmo "
                    + "inativados depois. O conjunto de autores enviado substitui o atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Livro atualizado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Categoria ou autor inexistente ou inativo, ou dado que viola uma regra "
                            + "do livro (ano de publicação futuro, por exemplo)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    BookResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBookRequest request) {
        var book = updateBookUseCase.update(new UpdateBookCommand(id, request.title(), request.publisher(),
                request.publicationYear(), request.categoryId(), request.authorIds()));
        return BookResponse.from(book);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inativar livro",
            description = "Inativa o livro, sem excluí-lo fisicamente (RN14). Um livro inativo não aceita "
                    + "exemplares novos. Inativar um livro já inativo não tem efeito.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Livro inativado"),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    void deactivate(@PathVariable Long id) {
        deactivateBookUseCase.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    @AdminOnly
    @Operation(summary = "Reativar livro",
            description = "Reativa um livro inativado (RN14), só para o administrador. O livro só volta se a categoria e todos os autores dele estão ativos; a recusa (422) diz quais estão inativos. Os exemplares não são alterados. Reativar um livro já ativo não tem efeito e devolve o livro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reativar reativado"),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Categoria ou algum autor do livro está inativo (a mensagem diz quais): reative-os antes",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    BookResponse reactivate(@PathVariable Long id) {
        return BookResponse.from(reactivateBookUseCase.reactivate(id));
    }
}
