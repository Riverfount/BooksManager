package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.adapter.in.web.ReaderOnly;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The catalog for the reader (Phase 6, RF05): the active books, authors and categories, to look for a book and see
 * whether it can be borrowed. It only reads, and it only ever shows active records: there is no {@code active}
 * parameter, and an inactive book is a 404 like one that does not exist. It reuses the search of the administrative
 * routes, so the filters, the order and the fixed number of queries per page are the same. What the reader sees of
 * a book is {@link ReaderBookResponse}: never the copies themselves.
 */
@ReaderOnly
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Reader catalog", description = "O acervo para o leitor: pesquisar livros, autores e categorias ativos.")
class ReaderCatalogController {

    private final ListBooksUseCase listBooksUseCase;
    private final GetBookUseCase getBookUseCase;
    private final ListAuthorsUseCase listAuthorsUseCase;
    private final ListCategoriesUseCase listCategoriesUseCase;

    ReaderCatalogController(ListBooksUseCase listBooksUseCase, GetBookUseCase getBookUseCase,
            ListAuthorsUseCase listAuthorsUseCase, ListCategoriesUseCase listCategoriesUseCase) {
        this.listBooksUseCase = listBooksUseCase;
        this.getBookUseCase = getBookUseCase;
        this.listAuthorsUseCase = listAuthorsUseCase;
        this.listCategoriesUseCase = listCategoriesUseCase;
    }

    @GetMapping("/books")
    @Operation(summary = "Pesquisar livros",
            description = "Lista os livros ativos de forma paginada (máximo de 100 por página), em ordem de título "
                    + "por padrão. Os filtros `title`, `author` e `isbn` casam por trecho, sem diferenciar "
                    + "maiúsculas (o título e o autor também ignoram acentos), a `categoryId` casa pelo id, e os "
                    + "filtros informados valem em conjunto (RF05). Cada livro traz `availableCopies`, a quantidade "
                    + "de exemplares disponíveis agora, e nada dos exemplares em si. Livro inativo nunca aparece. "
                    + "Só para o perfil de leitor. Tamanho de página acima de 100 é rejeitado com 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de livros"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<ReaderBookResponse> listBooks(
            @Parameter(description = "Trecho do título, sem diferenciar maiúsculas nem acentos", example = "casmurro")
            @RequestParam(required = false) String title,
            @Parameter(description = "Trecho do nome de um dos autores, sem diferenciar maiúsculas nem acentos", example = "machado")
            @RequestParam(required = false) String author,
            @Parameter(description = "Trecho do ISBN-13 guardado, com ou sem hífens", example = "978-85-3330")
            @RequestParam(required = false) String isbn,
            @Parameter(description = "Id da categoria", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, title, isbn, "
                    + "publicationYear", example = "title,asc")
            @RequestParam(defaultValue = ListBooksQuery.DEFAULT_SORT) String sort) {
        var query = new ListBooksQuery(title, author, isbn, categoryId, true, PageQuery.of(page, size, sort));
        return PageResponse.from(listBooksUseCase.list(query), ReaderBookResponse::from);
    }

    @GetMapping("/books/{id}")
    @Operation(summary = "Consultar um livro",
            description = "Devolve o livro ativo com o ISBN, o título, a editora, o ano, a categoria, os autores e a "
                    + "quantidade de exemplares disponíveis agora (`availableCopies`). Livro inativo ou inexistente "
                    + "devolve a mesma resposta 404. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Livro encontrado"),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado (ou inativo)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderBookResponse findBook(@PathVariable Long id) {
        return getBookUseCase.findById(id)
                .filter(item -> item.book().isActive())
                .map(ReaderBookResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("livro não encontrado: " + id));
    }

    @GetMapping("/authors")
    @Operation(summary = "Listar autores",
            description = "Lista os autores ativos de forma paginada (máximo de 100 por página), em ordem de nome "
                    + "por padrão, com o filtro `name` por trecho (sem diferenciar maiúsculas nem acentos). Só para "
                    + "o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de autores"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação ou ordenação inválidas (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<AuthorSummary> listAuthors(
            @Parameter(description = "Trecho do nome, sem diferenciar maiúsculas nem acentos", example = "machado")
            @RequestParam(required = false) String name,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, name", example = "name,asc")
            @RequestParam(defaultValue = ListAuthorsQuery.DEFAULT_SORT) String sort) {
        var query = new ListAuthorsQuery(name, true, PageQuery.of(page, size, sort));
        return PageResponse.from(listAuthorsUseCase.list(query), AuthorSummary::from);
    }

    @GetMapping("/categories")
    @Operation(summary = "Listar categorias",
            description = "Lista as categorias ativas de forma paginada (máximo de 100 por página), em ordem de nome "
                    + "por padrão, com o filtro `name` por trecho. Serve para escolher a `categoryId` da pesquisa de "
                    + "livros. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de categorias"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação ou ordenação inválidas (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<CategorySummary> listCategories(
            @Parameter(description = "Trecho do nome, sem diferenciar maiúsculas", example = "romance")
            @RequestParam(required = false) String name,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, name", example = "name,asc")
            @RequestParam(defaultValue = ListCategoriesQuery.DEFAULT_SORT) String sort) {
        var query = new ListCategoriesQuery(name, true, PageQuery.of(page, size, sort));
        return PageResponse.from(listCategoriesUseCase.list(query), CategorySummary::from);
    }
}
