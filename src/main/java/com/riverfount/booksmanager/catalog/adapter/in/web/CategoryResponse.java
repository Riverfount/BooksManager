package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

record CategoryResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador da categoria", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Nome da categoria", example = "Ficção")
        String name,
        @Schema(requiredMode = REQUIRED, description = "Se a categoria está ativa", example = "true")
        boolean active) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.isActive());
    }
}
