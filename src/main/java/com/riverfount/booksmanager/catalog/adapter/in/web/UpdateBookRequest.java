package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
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
        @Schema(description = "Nova editora, até 255 caracteres (opcional; não pode ficar em branco)", example = "Garnier")
        @Pattern(regexp = ".*\\S.*", message = "a editora não pode ficar em branco")
        @Size(max = Book.MAX_PUBLISHER_LENGTH, message = "a editora deve ter até " + Book.MAX_PUBLISHER_LENGTH
                + " caracteres")
        String publisher,
        @Schema(description = "Novo ano de publicação, de 1450 até o ano atual (opcional)", example = "1899")
        @Min(value = Book.MIN_PUBLICATION_YEAR, message = "o ano de publicação não pode ser anterior a "
                + Book.MIN_PUBLICATION_YEAR)
        Integer publicationYear,
        @Schema(description = "Nova categoria, já cadastrada e ativa (opcional)", example = "1")
        Long categoryId,
        @Schema(description = "Novo conjunto de autores, já cadastrados e ativos; substitui o atual "
                + "(opcional, mas não vazio quando enviado)", example = "[1]")
        @Size(min = 1, message = "é preciso informar ao menos um autor")
        Set<@NotNull(message = "o id do autor não pode ser nulo") Long> authorIds) {
}
