package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Book;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

record RegisterBookRequest(
        @NotBlank(message = "o ISBN é obrigatório")
        String isbn,
        @NotBlank(message = "o título é obrigatório")
        @Size(max = Book.MAX_TITLE_LENGTH, message = "o título deve ter até " + Book.MAX_TITLE_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o título não pode ter espaços nas pontas")
        String title,
        String publisher,
        Integer publicationYear,
        @NotNull(message = "a categoria é obrigatória")
        Long categoryId,
        @NotEmpty(message = "é preciso informar ao menos um autor")
        Set<@NotNull(message = "o id do autor não pode ser nulo") Long> authorIds) {
}
