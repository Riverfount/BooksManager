package com.riverfount.booksmanager.catalogo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

class AutorTest {

    @Test
    void deveCriarAutorNovoAtivoComONomeInformado() {
        var autor = Autor.novo("Machado de Assis");

        assertThat(autor.getId()).isNull();
        assertThat(autor.getNome()).isEqualTo("Machado de Assis");
        assertThat(autor.isAtivo()).isTrue();
    }

    @Test
    void deveReconstituirAutorComOEstadoPersistido() {
        var autor = Autor.reconstituir(1L, "Machado de Assis", false);

        assertThat(autor.getId()).isEqualTo(1L);
        assertThat(autor.getNome()).isEqualTo("Machado de Assis");
        assertThat(autor.isAtivo()).isFalse();
    }

    @Test
    void naoDeveCriarAutorComNomeNulo() {
        assertThatThrownBy(() -> Autor.novo(null)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarAutorComNomeEmBranco() {
        assertThatThrownBy(() -> Autor.novo("   ")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarAutorComNomeMaiorQueCentoECinquentaCaracteres() {
        var nomeComCentoECinquentaECincoCaracteres = "A".repeat(155);

        assertThatThrownBy(() -> Autor.novo(nomeComCentoECinquentaECincoCaracteres))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveAceitarNomeComExatamenteCentoECinquentaCaracteres() {
        var nomeComCentoECinquentaCaracteres = "A".repeat(150);

        var autor = Autor.novo(nomeComCentoECinquentaCaracteres);

        assertThat(autor.getNome()).isEqualTo(nomeComCentoECinquentaCaracteres);
    }

    @Test
    void deveRenomearAutor() {
        var autor = Autor.reconstituir(1L, "Machado de Assis", true);

        autor.renomear("Joaquim Maria Machado de Assis");

        assertThat(autor.getNome()).isEqualTo("Joaquim Maria Machado de Assis");
    }

    @Test
    void naoDeveRenomearComNomeInvalido() {
        var autor = Autor.reconstituir(1L, "Machado de Assis", true);

        assertThatThrownBy(() -> autor.renomear("")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveInativarAutor() {
        var autor = Autor.reconstituir(1L, "Machado de Assis", true);

        autor.inativar();

        assertThat(autor.isAtivo()).isFalse();
    }
}
