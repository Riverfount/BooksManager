package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Author;
import io.swagger.v3.oas.annotations.media.Schema;

record AuthorResponse(
        @Schema(description = "Identificador do autor", example = "1")
        Long id,
        @Schema(description = "Nome do autor", example = "Machado de Assis")
        String name,
        @Schema(description = "Se o autor está ativo", example = "true")
        boolean active) {

    static AuthorResponse from(Author author) {
        return new AuthorResponse(author.getId(), author.getName(), author.isActive());
    }
}
