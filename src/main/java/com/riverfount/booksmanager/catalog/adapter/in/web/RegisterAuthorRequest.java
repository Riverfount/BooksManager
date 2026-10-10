package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Author;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterAuthorRequest(
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = Author.MAX_NAME_LENGTH, message = "o nome deve ter até " + Author.MAX_NAME_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o nome não pode ter espaços nas pontas")
        String name) {
}
