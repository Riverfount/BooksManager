package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Partial update of a book: omitted (or null) fields keep their current
 * value; sent fields follow the same format rules as registration. There
 * is no isbn field (RN01): an isbn sent in the body is ignored.
 */
record UpdateBookRequest(
        @Schema(description = "Novo título do livro (opcional)", example = "Dom Casmurro")
        @Size(max = Book.MAX_TITLE_LENGTH, message = "o título deve ter até " + Book.MAX_TITLE_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$",
                message = "o título não pode ficar em branco nem ter espaços nas pontas")
        String title,
        @Schema(description = "Nova editora (opcional)", example = "Garnier")
        String publisher,
        @Schema(description = "Novo ano de publicação (opcional)", example = "1899")
        Integer publicationYear,
        @Schema(description = "Nova categoria, já cadastrada e ativa (opcional)", example = "1")
        Long categoryId,
        @Schema(description = "Novo conjunto de autores, já cadastrados e ativos; substitui o atual "
                + "(opcional, mas não vazio quando enviado)", example = "[1]")
        @Size(min = 1, message = "é preciso informar ao menos um autor")
        Set<@NotNull(message = "o id do autor não pode ser nulo") Long> authorIds) {
}
