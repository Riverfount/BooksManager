package com.riverfount.booksmanager.catalogo.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CadastrarCategoriaRequest(
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = 100, message = "o nome deve ter até 100 caracteres")
        String nome) {
}
