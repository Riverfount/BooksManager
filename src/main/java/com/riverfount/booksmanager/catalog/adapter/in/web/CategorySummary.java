package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

/** The category of a book as the book's response shows it: just enough to print the name. */
record CategorySummary(
        @Schema(requiredMode = REQUIRED, description = "Identificador da categoria", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Nome da categoria", example = "Romance")
        String name) {

    static CategorySummary from(Category category) {
        return new CategorySummary(category.getId(), category.getName());
    }
}
