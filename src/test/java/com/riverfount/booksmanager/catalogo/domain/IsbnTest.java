package com.riverfount.booksmanager.catalogo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

class IsbnTest {

    @Test
    void deveAceitarIsbn13Valido() {
        var isbn = new Isbn("9788533302273");

        assertThat(isbn.valor()).isEqualTo("9788533302273");
    }

    @Test
    void deveNormalizarRemovendoHifens() {
        var isbn = new Isbn("978-85-333-0227-3");

        assertThat(isbn.valor()).isEqualTo("9788533302273");
    }

    @Test
    void deveNormalizarRemovendoEspacos() {
        var isbn = new Isbn("978 85 333 0227 3");

        assertThat(isbn.valor()).isEqualTo("9788533302273");
    }

    @Test
    void deveAceitarIsbn10ValidoTerminadoEmX() {
        var isbn = new Isbn("080442957X");

        assertThat(isbn.valor()).isEqualTo("080442957X");
    }

    @Test
    void deveNormalizarXMinusculoParaMaiusculo() {
        var isbn = new Isbn("080442957x");

        assertThat(isbn.valor()).isEqualTo("080442957X");
    }

    @Test
    void naoDeveAceitarIsbnNulo() {
        assertThatThrownBy(() -> new Isbn(null)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveAceitarIsbn13ComDigitoVerificadorErrado() {
        assertThatThrownBy(() -> new Isbn("9788533302270")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveAceitarIsbn10ComDigitoVerificadorErrado() {
        assertThatThrownBy(() -> new Isbn("0804429571")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveAceitarTamanhoErrado() {
        assertThatThrownBy(() -> new Isbn("123456")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveAceitarCaracteresInvalidos() {
        assertThatThrownBy(() -> new Isbn("97885333022AB")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveAceitarXForaDaUltimaPosicaoNoIsbn10() {
        assertThatThrownBy(() -> new Isbn("X123456789")).isInstanceOf(RegraDeNegocioException.class);
    }
}
