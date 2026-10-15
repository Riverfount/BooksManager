package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.domain.Author;
import io.swagger.v3.oas.annotations.media.Schema;

record AuthorResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do autor", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Nome do autor", example = "Machado de Assis")
        String name,
        @Schema(requiredMode = REQUIRED, description = "Se o autor está ativo", example = "true")
        boolean active) {

    static AuthorResponse from(Author author) {
        return new AuthorResponse(author.getId(), author.getName(), author.isActive());
    }
}
