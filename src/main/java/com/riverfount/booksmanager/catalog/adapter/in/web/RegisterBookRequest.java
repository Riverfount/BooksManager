package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

record RegisterBookRequest(
        @Schema(description = "ISBN-10 ou ISBN-13, com ou sem hífens", example = "9788533302273")
        @NotBlank(message = "o ISBN é obrigatório")
        String isbn,
        @Schema(description = "Título do livro", example = "Dom Casmurro")
        @NotBlank(message = "o título é obrigatório")
        @Size(max = Book.MAX_TITLE_LENGTH, message = "o título deve ter até " + Book.MAX_TITLE_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o título não pode ter espaços nas pontas")
        String title,
        @Schema(description = "Editora", example = "Garnier")
        String publisher,
        @Schema(description = "Ano de publicação", example = "1899")
        Integer publicationYear,
        @Schema(description = "Id da categoria, já cadastrada e ativa", example = "1")
        @NotNull(message = "a categoria é obrigatória")
        Long categoryId,
        @Schema(description = "Ids dos autores, já cadastrados e ativos", example = "[1]")
        @NotEmpty(message = "é preciso informar ao menos um autor")
        Set<@NotNull(message = "o id do autor não pode ser nulo") Long> authorIds) {
}
