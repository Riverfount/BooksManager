package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

record CategoryResponse(
        @Schema(description = "Identificador da categoria", example = "1")
        Long id,
        @Schema(description = "Nome da categoria", example = "Ficção")
        String name,
        @Schema(description = "Se a categoria está ativa", example = "true")
        boolean active) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.isActive());
    }
}
