package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.domain.Author;
import io.swagger.v3.oas.annotations.media.Schema;

/** The author of a book as the book's response shows it: just enough to print the name. */
record AuthorSummary(
        @Schema(requiredMode = REQUIRED, description = "Identificador do autor", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Nome do autor", example = "Machado de Assis")
        String name) {

    static AuthorSummary from(Author author) {
        return new AuthorSummary(author.getId(), author.getName());
    }
}
