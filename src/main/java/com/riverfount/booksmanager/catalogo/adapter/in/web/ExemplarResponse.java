package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.domain.Exemplar;

record ExemplarResponse(Long id, Long livroId, String codigoPatrimonio, String status, boolean ativo) {

    static ExemplarResponse de(Exemplar exemplar) {
        return new ExemplarResponse(exemplar.getId(), exemplar.getLivroId(), exemplar.getCodigoPatrimonio(),
                exemplar.getStatus().name(), exemplar.isAtivo());
    }
}
