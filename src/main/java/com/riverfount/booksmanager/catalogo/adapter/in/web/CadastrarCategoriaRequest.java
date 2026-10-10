package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Categoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CadastrarCategoriaRequest(
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = Categoria.TAMANHO_MAXIMO_NOME, message = "o nome deve ter até " + Categoria.TAMANHO_MAXIMO_NOME
                + " caracteres")
        String nome) {
}
