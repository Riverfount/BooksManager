package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterCategoryRequest(
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = Category.MAX_NAME_LENGTH, message = "o nome deve ter até " + Category.MAX_NAME_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o nome não pode ter espaços nas pontas")
        String name) {
}
