package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.application.port.in.CategoryCopies;
import io.swagger.v3.oas.annotations.media.Schema;

record CategoryCopiesResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador da categoria", example = "1")
        Long categoryId,
        @Schema(requiredMode = REQUIRED, description = "Nome da categoria", example = "Romance")
        String categoryName,
        @Schema(requiredMode = REQUIRED, description = "Livros ativos da categoria", example = "30")
        long books,
        @Schema(requiredMode = REQUIRED, description = "Exemplares ativos dos livros da categoria", example = "80")
        long copies) {

    static CategoryCopiesResponse from(CategoryCopies category) {
        return new CategoryCopiesResponse(category.categoryId(), category.categoryName(), category.books(),
                category.copies());
    }
}
