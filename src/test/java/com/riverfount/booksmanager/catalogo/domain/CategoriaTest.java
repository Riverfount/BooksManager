package com.riverfount.booksmanager.catalogo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

class CategoriaTest {

    @Test
    void deveCriarCategoriaNovaAtivaComONomeInformado() {
        var categoria = Categoria.novo("Ficção");

        assertThat(categoria.getId()).isNull();
        assertThat(categoria.getNome()).isEqualTo("Ficção");
        assertThat(categoria.isAtivo()).isTrue();
    }

    @Test
    void deveReconstituirCategoriaComOEstadoPersistido() {
        var categoria = Categoria.reconstituir(1L, "Ficção", false);

        assertThat(categoria.getId()).isEqualTo(1L);
        assertThat(categoria.getNome()).isEqualTo("Ficção");
        assertThat(categoria.isAtivo()).isFalse();
    }

    @Test
    void naoDeveCriarCategoriaComNomeNulo() {
        assertThatThrownBy(() -> Categoria.novo(null))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarCategoriaComNomeEmBranco() {
        assertThatThrownBy(() -> Categoria.novo("   "))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarCategoriaComNomeComEspacoNaPonta() {
        assertThatThrownBy(() -> Categoria.novo(" Ficção"))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarCategoriaComNomeMaiorQueCemCaracteres() {
        var nomeComCentoECincoCaracteres = "A".repeat(105);

        assertThatThrownBy(() -> Categoria.novo(nomeComCentoECincoCaracteres))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveAceitarNomeComExatamenteCemCaracteres() {
        var nomeComCemCaracteres = "A".repeat(100);

        var categoria = Categoria.novo(nomeComCemCaracteres);

        assertThat(categoria.getNome()).isEqualTo(nomeComCemCaracteres);
    }

    @Test
    void deveRenomearCategoria() {
        var categoria = Categoria.reconstituir(1L, "Ficção", true);

        categoria.renomear("Não Ficção");

        assertThat(categoria.getNome()).isEqualTo("Não Ficção");
    }

    @Test
    void naoDeveRenomearComNomeInvalido() {
        var categoria = Categoria.reconstituir(1L, "Ficção", true);

        assertThatThrownBy(() -> categoria.renomear(""))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveInativarCategoria() {
        var categoria = Categoria.reconstituir(1L, "Ficção", true);

        categoria.inativar();

        assertThat(categoria.isAtivo()).isFalse();
    }
}
