package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Author;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterAuthorRequest(
        @Schema(description = "Nome do autor", example = "Machado de Assis")
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = Author.MAX_NAME_LENGTH, message = "o nome deve ter até " + Author.MAX_NAME_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o nome não pode ter espaços nas pontas")
        String name,
        @Schema(description = "Confirma que é um homônimo de um autor ativo: sem isso, um nome que um autor ativo já "
                + "tem é recusado (422, com o `existingAuthorId`). Opcional; o padrão é falso.", example = "false")
        Boolean allowDuplicate) {
}
