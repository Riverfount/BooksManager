package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Livro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

record CadastrarLivroRequest(
        @NotBlank(message = "o ISBN é obrigatório")
        String isbn,
        @NotBlank(message = "o título é obrigatório")
        @Size(max = Livro.TAMANHO_MAXIMO_TITULO, message = "o título deve ter até " + Livro.TAMANHO_MAXIMO_TITULO
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o título não pode ter espaços nas pontas")
        String titulo,
        String editora,
        Integer anoPublicacao,
        @NotNull(message = "a categoria é obrigatória")
        Long categoriaId,
        @NotEmpty(message = "é preciso informar ao menos um autor")
        Set<Long> autorIds) {
}
