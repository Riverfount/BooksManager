package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

/**
 * Testa o adaptador de persistência de Autor contra um PostgreSQL real
 * (Testcontainers). O contexto subir já prova que AutorJpaEntity bate com
 * a tabela criada pela V1 (ddl-auto=validate).
 */
@Import({TestcontainersConfiguration.class, AutorPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AutorPersistenceAdapterTest {

    @Autowired
    private AutorPersistenceAdapter adaptador;

    @Test
    void deveSalvarEBuscarAutorComOMesmoEstado() {
        var salvo = adaptador.salvar(Autor.novo("Machado de Assis"));

        var encontrado = adaptador.buscarPorId(salvo.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getId()).isEqualTo(salvo.getId());
        assertThat(encontrado.get().getNome()).isEqualTo("Machado de Assis");
        assertThat(encontrado.get().isAtivo()).isTrue();
    }

    @Test
    void deveDevolverVazioParaIdInexistente() {
        assertThat(adaptador.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void naoDeveRejeitarNomesDuplicados() {
        // diferente de Categoria, autor.nome não tem restrição única no
        // banco (dois autores podem, de fato, ter o mesmo nome)
        var primeiro = adaptador.salvar(Autor.novo("José Silva"));
        var segundo = adaptador.salvar(Autor.novo("José Silva"));

        assertThat(primeiro.getId()).isNotEqualTo(segundo.getId());
    }

    @Test
    void deveBuscarTodosPorIdsIgnorandoIdsInexistentes() {
        var autor1 = adaptador.salvar(Autor.novo("Autor Um"));
        var autor2 = adaptador.salvar(Autor.novo("Autor Dois"));

        var encontrados = adaptador.buscarTodosPorIds(Set.of(autor1.getId(), autor2.getId(), 999999L));

        assertThat(encontrados).extracting(Autor::getId).containsExactlyInAnyOrder(autor1.getId(), autor2.getId());
    }
}
