package com.riverfount.booksmanager.compartilhado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegraDeNegocioExceptionTest {

    @Test
    void deveGuardarAMensagemRecebida() {
        var excecao = new RegraDeNegocioException("o nome é obrigatório");

        assertThat(excecao.getMessage()).isEqualTo("o nome é obrigatório");
    }

    @Test
    void deveSerUmaExcecaoDeExecucaoNaoVerificada() {
        var excecao = new RegraDeNegocioException("qualquer mensagem");

        assertThat(excecao).isInstanceOf(RuntimeException.class);
    }
}
