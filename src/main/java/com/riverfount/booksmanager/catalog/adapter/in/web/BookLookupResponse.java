package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.application.port.in.BookLookupResult;
import com.riverfount.booksmanager.catalog.application.port.in.BookLookupResult.Outcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What the lookup of an ISBN answers (RF06): a suggestion to fill the registration of a book, never a registered book.
 * It is always a 200, even with the outside service off, slow or down (RNF19): the {@code outcome} and the
 * {@code message} say what happened, and the manual registration goes on.
 */
record BookLookupResponse(
        @Schema(requiredMode = REQUIRED, description = "ISBN como o acervo o guarda, sem hífens ou espaços",
                example = "9788533302273")
        String isbn,
        @Schema(requiredMode = REQUIRED, example = "FOUND",
                description = "O que aconteceu: FOUND (há sugestão), NOT_FOUND (a fonte externa não conhece o ISBN, "
                        + "está desligada, lenta ou fora do ar: cadastre à mão) ou ALREADY_REGISTERED (já existe "
                        + "um livro com este ISBN no acervo)")
        Outcome outcome,
        @Schema(requiredMode = REQUIRED, description = "Explicação em português do que aconteceu, para mostrar ao "
                + "operador", example = "Dados sugeridos por uma fonte externa; confira antes de cadastrar.")
        String message,
        @Schema(description = "Id do livro que já tem este ISBN; só vem com ALREADY_REGISTERED", example = "7")
        Long registeredBookId,
        @Schema(description = "Dados para pré-preencher o cadastro; só vêm com FOUND") Suggestion suggestion) {

    record Suggestion(
            @Schema(requiredMode = REQUIRED, description = "Título sugerido", example = "Dom Casmurro")
            String title,
            @Schema(description = "Editora sugerida", example = "Garnier")
            String publisher,
            @Schema(description = "Ano de publicação sugerido", example = "1899")
            Integer publicationYear,
            @Schema(requiredMode = REQUIRED, description = "Autores sugeridos, na ordem da fonte")
            List<SuggestedAuthor> authors) {
    }

    record SuggestedAuthor(
            @Schema(requiredMode = REQUIRED, description = "Nome do autor como a fonte o dá",
                    example = "Machado de Assis")
            String name,
            @Schema(description = "Id do autor ativo do acervo com este nome (sem diferenciar maiúsculas), para o "
                    + "front usá-lo em vez de cadastrar de novo; vem vazio quando o acervo não tem", example = "3")
            Long existingAuthorId) {
    }

    static BookLookupResponse from(BookLookupResult result) {
        return switch (result.outcome()) {
            case FOUND -> new BookLookupResponse(result.isbn(), Outcome.FOUND,
                    "Dados sugeridos por uma fonte externa; confira antes de cadastrar.", null,
                    new Suggestion(result.suggestion().title(), result.suggestion().publisher(),
                            result.suggestion().publicationYear(),
                            result.suggestion().authors().stream()
                                    .map(author -> new SuggestedAuthor(author.name(), author.existingAuthorId()))
                                    .toList()));
            case NOT_FOUND -> new BookLookupResponse(result.isbn(), Outcome.NOT_FOUND,
                    "Nenhum dado encontrado para este ISBN, ou a consulta externa está indisponível; "
                            + "preencha o cadastro manualmente.", null, null);
            case ALREADY_REGISTERED -> new BookLookupResponse(result.isbn(), Outcome.ALREADY_REGISTERED,
                    "Já existe um livro cadastrado com este ISBN.", result.registeredBookId(), null);
        };
    }
}
