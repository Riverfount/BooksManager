package com.riverfount.booksmanager.catalogo.application.port.in;

import java.util.Set;

public record CadastrarLivroCommand(String isbn, String titulo, String editora, Integer anoPublicacao,
        Long categoriaId, Set<Long> autorIds) {
}
