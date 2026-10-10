package com.riverfount.booksmanager.catalogo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

class ExemplarTest {

    @Test
    void deveCriarExemplarNovoDisponivelEAtivoComVersaoZero() {
        var exemplar = Exemplar.novo(1L, "PAT-1");

        assertThat(exemplar.getId()).isNull();
        assertThat(exemplar.getLivroId()).isEqualTo(1L);
        assertThat(exemplar.getCodigoPatrimonio()).isEqualTo("PAT-1");
        assertThat(exemplar.getStatus()).isEqualTo(StatusExemplar.DISPONIVEL);
        assertThat(exemplar.isAtivo()).isTrue();
        assertThat(exemplar.getVersao()).isEqualTo(0L);
    }

    @Test
    void deveReconstituirExemplarComOEstadoPersistido() {
        var exemplar = Exemplar.reconstituir(1L, 2L, "PAT-1", StatusExemplar.EMPRESTADO, true, 5L);

        assertThat(exemplar.getId()).isEqualTo(1L);
        assertThat(exemplar.getLivroId()).isEqualTo(2L);
        assertThat(exemplar.getStatus()).isEqualTo(StatusExemplar.EMPRESTADO);
        assertThat(exemplar.getVersao()).isEqualTo(5L);
    }

    @Test
    void naoDeveCriarExemplarComLivroIdNulo() {
        assertThatThrownBy(() -> Exemplar.novo(null, "PAT-1")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarExemplarComCodigoPatrimonioEmBranco() {
        assertThatThrownBy(() -> Exemplar.novo(1L, "  ")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveEmprestarExemplarDisponivelEAtivo() {
        var exemplar = Exemplar.novo(1L, "PAT-1");

        exemplar.emprestar();

        assertThat(exemplar.getStatus()).isEqualTo(StatusExemplar.EMPRESTADO);
    }

    @Test
    void naoDeveEmprestarExemplarJaEmprestado() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.EMPRESTADO, true, 0L);

        assertThatThrownBy(exemplar::emprestar).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveEmprestarExemplarReservado() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.RESERVADO, true, 0L);

        assertThatThrownBy(exemplar::emprestar).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveEmprestarExemplarInativo() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.DISPONIVEL, false, 0L);

        assertThatThrownBy(exemplar::emprestar).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveDevolverExemplarEmprestado() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.EMPRESTADO, true, 0L);

        exemplar.devolver();

        assertThat(exemplar.getStatus()).isEqualTo(StatusExemplar.DISPONIVEL);
    }

    @Test
    void naoDeveDevolverExemplarQueNaoEstaEmprestado() {
        var exemplar = Exemplar.novo(1L, "PAT-1");

        assertThatThrownBy(exemplar::devolver).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveMarcarExemplarComoDanificado() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.EMPRESTADO, true, 0L);

        exemplar.marcarDanificado();

        assertThat(exemplar.getStatus()).isEqualTo(StatusExemplar.DANIFICADO);
    }

    @Test
    void deveMarcarExemplarComoExtraviado() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.EMPRESTADO, true, 0L);

        exemplar.marcarExtraviado();

        assertThat(exemplar.getStatus()).isEqualTo(StatusExemplar.EXTRAVIADO);
    }

    @Test
    void deveInativarExemplarDisponivel() {
        var exemplar = Exemplar.novo(1L, "PAT-1");

        exemplar.inativar();

        assertThat(exemplar.isAtivo()).isFalse();
    }

    @Test
    void deveInativarExemplarDanificado() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.DANIFICADO, true, 0L);

        exemplar.inativar();

        assertThat(exemplar.isAtivo()).isFalse();
    }

    @Test
    void naoDeveInativarExemplarEmprestado() {
        var exemplar = Exemplar.reconstituir(1L, 1L, "PAT-1", StatusExemplar.EMPRESTADO, true, 0L);

        assertThatThrownBy(exemplar::inativar).isInstanceOf(RegraDeNegocioException.class);
    }
}
