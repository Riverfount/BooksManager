package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

record RegisterBookRequest(
        @Schema(description = "ISBN-10 ou ISBN-13, com ou sem hífens; o ISBN-10 é guardado como o ISBN-13 do mesmo livro",
                example = "9788533302273")
        @NotBlank(message = "o ISBN é obrigatório")
        String isbn,
        @Schema(description = "Título do livro", example = "Dom Casmurro")
        @NotBlank(message = "o título é obrigatório")
        @Size(max = Book.MAX_TITLE_LENGTH, message = "o título deve ter até " + Book.MAX_TITLE_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o título não pode ter espaços nas pontas")
        String title,
        @Schema(description = "Editora (até 255 caracteres)", example = "Garnier")
        @Size(max = Book.MAX_PUBLISHER_LENGTH, message = "a editora deve ter até " + Book.MAX_PUBLISHER_LENGTH
                + " caracteres")
        String publisher,
        @Schema(description = "Ano de publicação (de 1450 até o ano atual)", example = "1899")
        @Min(value = Book.MIN_PUBLICATION_YEAR, message = "o ano de publicação não pode ser anterior a "
                + Book.MIN_PUBLICATION_YEAR)
        Integer publicationYear,
        @Schema(description = "Id da categoria, já cadastrada e ativa", example = "1")
        @NotNull(message = "a categoria é obrigatória")
        Long categoryId,
        @Schema(description = "Ids dos autores, já cadastrados e ativos", example = "[1]")
        @NotEmpty(message = "é preciso informar ao menos um autor")
        Set<@NotNull(message = "o id do autor não pode ser nulo") Long> authorIds) {
}
