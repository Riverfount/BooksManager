package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Author;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of an author: omitted (or null) fields keep their
 * current value; sent fields follow the same format rules as registration.
 */
record UpdateAuthorRequest(
        @Schema(description = "Novo nome do autor (opcional)", example = "Machado de Assis")
        @Size(max = Author.MAX_NAME_LENGTH, message = "o nome deve ter até " + Author.MAX_NAME_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$",
                message = "o nome não pode ficar em branco nem ter espaços nas pontas")
        String name) {
}
