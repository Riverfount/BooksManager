package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testa o adaptador de persistência de Livro contra um PostgreSQL real
 * (Testcontainers), incluindo a associação N:N de autores mapeada como
 * coleção de IDs (livro_autor).
 */
@Import({TestcontainersConfiguration.class, LivroPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class LivroPersistenceAdapterTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private LivroPersistenceAdapter adaptador;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    private Long categoriaId;
    private Long autorId1;
    private Long autorId2;

    @BeforeEach
    void preparar() {
        categoriaId = jdbcTemplate.queryForObject(
                "insert into categoria (nome, ativo) values ('Ficção', true) returning id", Long.class);
        autorId1 = jdbcTemplate.queryForObject(
                "insert into autor (nome, ativo) values ('Autor Um', true) returning id", Long.class);
        autorId2 = jdbcTemplate.queryForObject(
                "insert into autor (nome, ativo) values ('Autor Dois', true) returning id", Long.class);
    }

    @Test
    void deveSalvarEBuscarLivroComOMesmoEstado() {
        var livro = Livro.novo(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoriaId,
                Set.of(autorId1, autorId2), RELOGIO_FIXO);

        var salvo = adaptador.salvar(livro);
        var encontrado = adaptador.buscarPorId(salvo.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getIsbn()).isEqualTo(new Isbn("9788533302273"));
        assertThat(encontrado.get().getTitulo()).isEqualTo("Dom Casmurro");
        assertThat(encontrado.get().getEditora()).isEqualTo("Editora X");
        assertThat(encontrado.get().getAnoPublicacao()).isEqualTo(1899);
        assertThat(encontrado.get().getCategoriaId()).isEqualTo(categoriaId);
        assertThat(encontrado.get().getAutorIds()).containsExactlyInAnyOrder(autorId1, autorId2);
        assertThat(encontrado.get().isAtivo()).isTrue();
    }

    @Test
    void deveGravarDuasLinhasEmLivroAutorAoSalvarLivroComDoisAutores() {
        var livro = Livro.novo(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoriaId,
                Set.of(autorId1, autorId2), RELOGIO_FIXO);

        var salvo = adaptador.salvar(livro);
        entityManager.flush();

        var totalLinhas = jdbcTemplate.queryForObject(
                "select count(*) from livro_autor where livro_id = ?", Integer.class, salvo.getId());
        assertThat(totalLinhas).isEqualTo(2);
    }

    @Test
    void violacaoDeChaveEstrangeiraNaoDeveSerConfundidaComIsbnDuplicado() {
        var livroComCategoriaInexistente = Livro.novo(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899,
                999999L, Set.of(autorId1), RELOGIO_FIXO);

        assertThatThrownBy(() -> adaptador.salvar(livroComCategoriaInexistente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void isbnDuplicadoDeveViolarARestricaoUnicaEVirarRegraDeNegocioException() {
        adaptador.salvar(Livro.novo(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoriaId,
                Set.of(autorId1), RELOGIO_FIXO));

        assertThatThrownBy(() -> adaptador.salvar(Livro.novo(new Isbn("9788533302273"), "Outro Livro", "Editora Y",
                1900, categoriaId, Set.of(autorId2), RELOGIO_FIXO)))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void existeComIsbnDeveRefletirOQueJaFoiSalvo() {
        adaptador.salvar(Livro.novo(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoriaId,
                Set.of(autorId1), RELOGIO_FIXO));

        assertThat(adaptador.existeComIsbn(new Isbn("9788533302273"))).isTrue();
        assertThat(adaptador.existeComIsbn(new Isbn("080442957X"))).isFalse();
    }

    @Test
    void deveDevolverVazioParaIdInexistente() {
        assertThat(adaptador.buscarPorId(-1L)).isEmpty();
    }
}
