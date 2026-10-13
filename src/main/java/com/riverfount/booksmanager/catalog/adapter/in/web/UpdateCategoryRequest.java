package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a category: omitted (or null) fields keep their
 * current value; sent fields follow the same format rules as registration.
 */
record UpdateCategoryRequest(
        @Schema(description = "Novo nome da categoria (opcional)", example = "Ficção")
        @Size(max = Category.MAX_NAME_LENGTH, message = "o nome deve ter até " + Category.MAX_NAME_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$",
                message = "o nome não pode ficar em branco nem ter espaços nas pontas")
        String name) {
}
