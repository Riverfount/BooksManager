package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Livro;
import java.util.Set;

record LivroResponse(Long id, String isbn, String titulo, String editora, Integer anoPublicacao, Long categoriaId,
        Set<Long> autorIds, boolean ativo) {

    static LivroResponse de(Livro livro) {
        return new LivroResponse(livro.getId(), livro.getIsbn().valor(), livro.getTitulo(), livro.getEditora(),
                livro.getAnoPublicacao(), livro.getCategoriaId(), livro.getAutorIds(), livro.isAtivo());
    }
}
