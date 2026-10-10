package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CadastrarExemplarRequest(
        @NotNull(message = "o livro é obrigatório")
        Long livroId,
        @NotBlank(message = "o código de patrimônio é obrigatório")
        @Size(max = Exemplar.TAMANHO_MAXIMO_CODIGO_PATRIMONIO,
                message = "o código de patrimônio deve ter até " + Exemplar.TAMANHO_MAXIMO_CODIGO_PATRIMONIO
                        + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o código de patrimônio não pode ter espaços nas pontas")
        String codigoPatrimonio) {
}
