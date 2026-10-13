package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void shouldUpdateExistingAuthorInsteadOfInsertingANewOne() {
        var saved = adapter.save(Author.create("Machado de Asis"));
        saved.rename("Machado de Assis");

        var updated = adapter.save(saved);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(adapter.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Machado de Assis");
        assertThat(adapter.findAll(null, true, PageQuery.of(0, 20, "name")).totalItems()).isEqualTo(1);
    }

    @Test
    void shouldPersistDeactivationKeepingTheRowAndTheBookAssociation() {
        // RN14: deactivating is an update, never a physical delete
        var author = adapter.save(Author.create("Machado de Assis"));
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name, active) values ('Romance', true) returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, publisher, publication_year, category_id, active)
                values ('9788533302273', 'Dom Casmurro', 'Editora X', 1899, ?, true) returning id
                """, Long.class, categoryId);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", bookId, author.getId());

        author.deactivate();
        adapter.save(author);

        var found = adapter.findById(author.getId());
        assertThat(found).isPresent();
        assertThat(found.get().isActive()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from book_author where book_id = ? and author_id = ?",
                Long.class, bookId, author.getId())).isEqualTo(1);
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

    @Test
    void shouldListActiveAuthorsPaginatedAndSortedByName() {
        adapter.save(Author.create("Autor C"));
        adapter.save(Author.create("Autor A"));
        adapter.save(Author.create("Autor B"));
        adapter.save(Author.reconstitute(null, "Autor Inativo", false));

        var firstPage = adapter.findAll(null, true, PageQuery.of(0, 2, "name"));
        var secondPage = adapter.findAll(null, true, PageQuery.of(1, 2, "name"));

        assertThat(firstPage.items()).extracting(Author::getName).containsExactly("Autor A", "Autor B");
        assertThat(secondPage.items()).extracting(Author::getName).containsExactly("Autor C");
        assertThat(firstPage.totalItems()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldListInactiveAuthorsWhenRequested() {
        adapter.save(Author.create("Autor Ativo"));
        adapter.save(Author.reconstitute(null, "Autor Inativo", false));

        var result = adapter.findAll(null, false, PageQuery.of(0, 20, "name"));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Autor Inativo");
    }

    @Test
    void shouldFilterByNameContainingIgnoringCase() {
        adapter.save(Author.create("Machado de Assis"));
        adapter.save(Author.create("Clarice Lispector"));

        var result = adapter.findAll("ASSIS", true, PageQuery.of(0, 20, "name"));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Machado de Assis");
    }

    @Test
    void shouldTreatLikeWildcardsInNameFilterLiterally() {
        adapter.save(Author.create("Autor 100% Real"));
        adapter.save(Author.create("Autor Comum"));

        var result = adapter.findAll("%", true, PageQuery.of(0, 20, "name"));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Autor 100% Real");
    }

    @Test
    void shouldSortByIdDescending() {
        var first = adapter.save(Author.create("Autor A"));
        var second = adapter.save(Author.create("Autor B"));

        var result = adapter.findAll(null, true, PageQuery.of(0, 20, "id,desc"));

        assertThat(result.items()).extracting(Author::getId).containsExactly(second.getId(), first.getId());
    }
}
