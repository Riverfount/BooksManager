package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import com.riverfount.booksmanager.catalogo.domain.StatusExemplar;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Testa o adaptador de persistência de Exemplar contra um PostgreSQL real
 * (Testcontainers), incluindo o lock otimista de verdade (RNF17), via
 * {@code @Version} na entidade JPA.
 */
@Import({TestcontainersConfiguration.class, ExemplarPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ExemplarPersistenceAdapterTest {

    @Autowired
    private ExemplarPersistenceAdapter adaptador;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long livroId;

    @BeforeEach
    void preparar() {
        var categoriaId = jdbcTemplate.queryForObject(
                "insert into categoria (nome, ativo) values ('Ficção', true) returning id", Long.class);
        livroId = jdbcTemplate.queryForObject(
                """
                insert into livro (isbn, titulo, categoria_id, ativo)
                values ('9788533302273', 'Dom Casmurro', ?, true) returning id
                """,
                Long.class, categoriaId);
    }

    @Test
    void deveSalvarEBuscarExemplarComOMesmoEstado() {
        var salvo = adaptador.salvar(Exemplar.novo(livroId, "PAT-1"));

        var encontrado = adaptador.buscarPorId(salvo.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getId()).isEqualTo(salvo.getId());
        assertThat(encontrado.get().getLivroId()).isEqualTo(livroId);
        assertThat(encontrado.get().getCodigoPatrimonio()).isEqualTo("PAT-1");
        assertThat(encontrado.get().getStatus()).isEqualTo(StatusExemplar.DISPONIVEL);
        assertThat(encontrado.get().isAtivo()).isTrue();
        assertThat(encontrado.get().getVersao()).isEqualTo(0L);
    }

    @Test
    void deveDevolverVazioParaIdInexistente() {
        assertThat(adaptador.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void codigoPatrimonioDuplicadoDeveViolarARestricaoUnicaEVirarRegraDeNegocioException() {
        adaptador.salvar(Exemplar.novo(livroId, "PAT-1"));

        assertThatThrownBy(() -> adaptador.salvar(Exemplar.novo(livroId, "PAT-1")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void violacaoDeChaveEstrangeiraNaoDeveSerConfundidaComCodigoPatrimonioDuplicado() {
        var exemplarComLivroInexistente = Exemplar.novo(999999L, "PAT-1");

        assertThatThrownBy(() -> adaptador.salvar(exemplarComLivroInexistente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void existeCodigoPatrimonioDeveRefletirOQueJaFoiSalvo() {
        adaptador.salvar(Exemplar.novo(livroId, "PAT-1"));

        assertThat(adaptador.existeCodigoPatrimonio("PAT-1")).isTrue();
        assertThat(adaptador.existeCodigoPatrimonio("PAT-INEXISTENTE")).isFalse();
    }

    @Test
    void deveManterOStatusAoSalvarEBuscar() {
        var salvo = adaptador.salvar(Exemplar.novo(livroId, "PAT-1"));
        salvo.emprestar();

        var atualizado = adaptador.salvar(salvo);
        var encontrado = adaptador.buscarPorId(atualizado.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getStatus()).isEqualTo(StatusExemplar.EMPRESTADO);
        assertThat(encontrado.get().getVersao()).isEqualTo(1L);
    }

    @Test
    void deveFalharComObjectOptimisticLockingFailureExceptionAoSalvarDuasCopiasConcorrentes() {
        var original = adaptador.salvar(Exemplar.novo(livroId, "PAT-CONCORRENCIA"));

        // carrega o mesmo exemplar duas vezes, simulando duas requisições
        // concorrentes que leram o mesmo estado (versão 0)
        var copia1 = adaptador.buscarPorId(original.getId()).orElseThrow();
        var copia2 = adaptador.buscarPorId(original.getId()).orElseThrow();

        copia1.emprestar();
        copia2.emprestar();

        adaptador.salvar(copia1); // sucesso: versão no banco vira 1

        assertThatThrownBy(() -> adaptador.salvar(copia2)) // copia2 ainda carrega a versão 0
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
