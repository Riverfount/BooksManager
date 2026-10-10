package com.riverfount.booksmanager.compartilhado.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ViolacoesDeRestricaoTest {

    @Test
    void deveReconhecerQuandoACausaMaisEspecificaMencionaONomeDaConstraint() {
        var causa = new RuntimeException("duplicate key value violates unique constraint \"livro_isbn_key\"");
        var excecao = new DataIntegrityViolationException("erro ao salvar", causa);

        assertThat(ViolacoesDeRestricao.viola(excecao, "livro_isbn_key")).isTrue();
    }

    @Test
    void naoDeveReconhecerQuandoACausaMencionaOutraConstraint() {
        var causa = new RuntimeException(
                "insert or update on table \"livro\" violates foreign key constraint \"livro_categoria_id_fkey\"");
        var excecao = new DataIntegrityViolationException("erro ao salvar", causa);

        assertThat(ViolacoesDeRestricao.viola(excecao, "livro_isbn_key")).isFalse();
    }

    @Test
    void naoDeveReconhecerQuandoNaoHaCausaComMensagemCompativel() {
        var excecao = new DataIntegrityViolationException("erro genérico, sem a constraint na mensagem");

        assertThat(ViolacoesDeRestricao.viola(excecao, "livro_isbn_key")).isFalse();
    }
}
