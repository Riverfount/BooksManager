package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Categoria;

record CategoriaResponse(Long id, String nome, boolean ativo) {

    static CategoriaResponse de(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.isAtivo());
    }
}
