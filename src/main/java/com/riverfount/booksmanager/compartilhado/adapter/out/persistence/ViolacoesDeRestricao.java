package com.riverfount.booksmanager.compartilhado.adapter.out.persistence;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifica qual restrição do banco foi violada numa
 * DataIntegrityViolationException, para que os adaptadores de
 * persistência só traduzam para RegraDeNegocioException a violação
 * esperada (ex.: nome único), sem confundir com outras causas (ex.:
 * chave estrangeira inválida).
 */
public final class ViolacoesDeRestricao {

    private ViolacoesDeRestricao() {
    }

    public static boolean viola(DataIntegrityViolationException excecao, String nomeDaConstraint) {
        var mensagem = excecao.getMostSpecificCause().getMessage();
        return mensagem != null && mensagem.contains(nomeDaConstraint);
    }
}
