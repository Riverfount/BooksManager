package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Autor;

record AutorResponse(Long id, String nome, boolean ativo) {

    static AutorResponse de(Autor autor) {
        return new AutorResponse(autor.getId(), autor.getNome(), autor.isAtivo());
    }
}
