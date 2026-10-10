package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Autor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CadastrarAutorRequest(
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = Autor.TAMANHO_MAXIMO_NOME, message = "o nome deve ter até " + Autor.TAMANHO_MAXIMO_NOME
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o nome não pode ter espaços nas pontas")
        String nome) {
}
