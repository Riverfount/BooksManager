package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.domain.Author;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

/**
 * Tests the Author persistence adapter against a real PostgreSQL
 * (Testcontainers). The context starting up already proves that
 * AuthorJpaEntity matches the table created by V1 (ddl-auto=validate).
 */
@Import({TestcontainersConfiguration.class, AuthorPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AuthorPersistenceAdapterTest {

    @Autowired
    private AuthorPersistenceAdapter adapter;

    @Test
    void shouldSaveAndFindAuthorWithTheSameState() {
        var saved = adapter.save(Author.create("Machado de Assis"));

        var found = adapter.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getName()).isEqualTo("Machado de Assis");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void shouldReturnEmptyForNonExistentId() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }

    @Test
    void shouldNotRejectDuplicateNames() {
        // unlike Category, author.name has no unique constraint in the
        // database (two authors can, in fact, have the same name)
        var first = adapter.save(Author.create("José Silva"));
        var second = adapter.save(Author.create("José Silva"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void shouldFindAllByIdsIgnoringNonExistentIds() {
        var author1 = adapter.save(Author.create("Autor Um"));
        var author2 = adapter.save(Author.create("Autor Dois"));

        var found = adapter.findAllByIds(Set.of(author1.getId(), author2.getId(), 999999L));

        assertThat(found).extracting(Author::getId).containsExactlyInAnyOrder(author1.getId(), author2.getId());
    }
}
