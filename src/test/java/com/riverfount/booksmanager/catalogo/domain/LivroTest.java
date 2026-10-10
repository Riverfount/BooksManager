package com.riverfount.booksmanager.catalogo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LivroTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    private static final Isbn ISBN = new Isbn("9788533302273");

    @Test
    void deveCriarLivroNovoAtivoComOsDadosInformados() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L, 20L), RELOGIO_FIXO);

        assertThat(livro.getId()).isNull();
        assertThat(livro.getIsbn()).isEqualTo(ISBN);
        assertThat(livro.getTitulo()).isEqualTo("Dom Casmurro");
        assertThat(livro.getEditora()).isEqualTo("Editora X");
        assertThat(livro.getAnoPublicacao()).isEqualTo(1899);
        assertThat(livro.getCategoriaId()).isEqualTo(1L);
        assertThat(livro.getAutorIds()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(livro.isAtivo()).isTrue();
    }

    @Test
    void deveReconstituirLivroComOEstadoPersistido() {
        var livro = Livro.reconstituir(1L, ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), false);

        assertThat(livro.getId()).isEqualTo(1L);
        assertThat(livro.isAtivo()).isFalse();
    }

    @Test
    void deveAceitarAnoDePublicacaoNulo() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", null, 1L, Set.of(10L), RELOGIO_FIXO);

        assertThat(livro.getAnoPublicacao()).isNull();
    }

    @Test
    void deveAceitarAnoDePublicacaoIgualAoAnoAtual() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 2026, 1L, Set.of(10L), RELOGIO_FIXO);

        assertThat(livro.getAnoPublicacao()).isEqualTo(2026);
    }

    @Test
    void naoDeveAceitarAnoDePublicacaoFuturo() {
        assertThatThrownBy(() -> Livro.novo(ISBN, "Dom Casmurro", "Editora X", 2027, 1L, Set.of(10L), RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarLivroComIsbnNulo() {
        assertThatThrownBy(() -> Livro.novo(null, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarLivroComTituloEmBranco() {
        assertThatThrownBy(() -> Livro.novo(ISBN, "  ", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarLivroComCategoriaNula() {
        assertThatThrownBy(() -> Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, null, Set.of(10L), RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarLivroSemAutor() {
        assertThatThrownBy(() -> Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(), RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCriarLivroComAutorIdsNulo() {
        assertThatThrownBy(() -> Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, null, RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveAtualizarDadosDoLivro() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        livro.atualizarDados("Dom Casmurro (revisado)", "Editora Y", 1900, 2L, RELOGIO_FIXO);

        assertThat(livro.getTitulo()).isEqualTo("Dom Casmurro (revisado)");
        assertThat(livro.getEditora()).isEqualTo("Editora Y");
        assertThat(livro.getAnoPublicacao()).isEqualTo(1900);
        assertThat(livro.getCategoriaId()).isEqualTo(2L);
    }

    @Test
    void naoDeveAtualizarComTituloInvalido() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        assertThatThrownBy(() -> livro.atualizarDados("", "Editora X", 1899, 1L, RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveAtualizarComAnoDePublicacaoFuturo() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        assertThatThrownBy(() -> livro.atualizarDados("Dom Casmurro", "Editora X", 2027, 1L, RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveAtualizarComCategoriaNula() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        assertThatThrownBy(() -> livro.atualizarDados("Dom Casmurro", "Editora X", 1899, null, RELOGIO_FIXO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveAdicionarAutor() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        livro.adicionarAutor(20L);

        assertThat(livro.getAutorIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void naoDeveAdicionarAutorNulo() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        assertThatThrownBy(() -> livro.adicionarAutor(null)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveRemoverAutorQuandoHaMaisDeUm() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L, 20L), RELOGIO_FIXO);

        livro.removerAutor(10L);

        assertThat(livro.getAutorIds()).containsExactly(20L);
    }

    @Test
    void naoDeveRemoverOUltimoAutor() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        assertThatThrownBy(() -> livro.removerAutor(10L)).isInstanceOf(RegraDeNegocioException.class);
        assertThat(livro.getAutorIds()).containsExactly(10L);
    }

    @Test
    void deveInativarLivro() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);

        livro.inativar();

        assertThat(livro.isAtivo()).isFalse();
    }

    @Test
    void autorIdsExpostoDeveSerUmaCopiaImutavel() {
        var livro = Livro.novo(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), RELOGIO_FIXO);
        var autorIds = livro.getAutorIds();

        assertThatThrownBy(() -> autorIds.add(99L)).isInstanceOf(UnsupportedOperationException.class);

        livro.adicionarAutor(30L);
        assertThat(autorIds).doesNotContain(30L);
    }
}
