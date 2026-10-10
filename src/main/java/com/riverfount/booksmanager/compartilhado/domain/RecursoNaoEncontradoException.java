package com.riverfount.booksmanager.compartilhado.domain;

/**
 * Sinaliza que um recurso buscado por identificador não existe (ou não
 * existe mais). Mapeada para o status 404 pelo TratadorDeExcecaoGlobal.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
