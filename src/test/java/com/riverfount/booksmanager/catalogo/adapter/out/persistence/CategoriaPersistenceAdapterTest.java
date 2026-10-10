package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Testa o adaptador de persistência de Categoria contra um PostgreSQL real
 * (Testcontainers). O contexto subir já prova que CategoriaJpaEntity bate
 * com a tabela criada pela V1 (ddl-auto=validate).
 */
@Import({TestcontainersConfiguration.class, CategoriaPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CategoriaPersistenceAdapterTest {

    @Autowired
    private CategoriaPersistenceAdapter adaptador;

    @Test
    void deveSalvarEBuscarCategoriaComOMesmoEstado() {
        var salva = adaptador.salvar(Categoria.novo("Ficção"));

        var encontrada = adaptador.buscarPorId(salva.getId());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getId()).isEqualTo(salva.getId());
        assertThat(encontrada.get().getNome()).isEqualTo("Ficção");
        assertThat(encontrada.get().isAtivo()).isTrue();
    }

    @Test
    void deveDevolverVazioParaIdInexistente() {
        assertThat(adaptador.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void existeComNomeDeveRefletirOQueJaFoiSalvo() {
        adaptador.salvar(Categoria.novo("Suspense"));

        assertThat(adaptador.existeComNome("Suspense")).isTrue();
        assertThat(adaptador.existeComNome("Nome inexistente")).isFalse();
    }

    @Test
    void nomeDuplicadoDeveViolarARestricaoUnicaDoBanco() {
        adaptador.salvar(Categoria.novo("Terror"));

        assertThatThrownBy(() -> adaptador.salvar(Categoria.novo("Terror")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
