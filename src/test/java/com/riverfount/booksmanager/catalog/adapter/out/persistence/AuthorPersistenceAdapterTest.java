package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the Author persistence adapter against a real PostgreSQL
 * (Testcontainers). The context starting up already proves that
 * AuthorJpaEntity matches the table created by V1 (ddl-auto=validate).
 */
@PersistenceTest
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

    @Test
    void shouldFindTheActiveAuthorsByNameIgnoringCaseInOneCall() {
        var machado = adapter.save(Author.create("Machado de Assis"));
        var clarice = adapter.save(Author.create("Clarice Lispector"));
        var inactive = Author.create("Graciliano Ramos");
        inactive.deactivate();
        adapter.save(inactive);
        adapter.save(Author.create("Outro Autor"));

        var found = adapter.findActiveByNames(Set.of("MACHADO DE ASSIS", "clarice lispector", "Graciliano Ramos",
                "Ninguém"));

        assertThat(found).extracting(Author::getId).containsExactlyInAnyOrder(machado.getId(), clarice.getId());
    }

    @Test
    void shouldFindNoAuthorsForNoNames() {
        assertThat(adapter.findActiveByNames(Set.of())).isEmpty();
    }

    // the search by name ignores accents (RF05)

    @Test
    void shouldFilterByNameIgnoringAccentsInEitherDirection() {
        adapter.save(Author.create("Gabriel García Márquez"));
        adapter.save(Author.create("Jose Saramago"));
        adapter.save(Author.create("Clarice Lispector"));

        assertThat(adapter.findAll("garcia marquez", true, PageQuery.of(0, 20, "name")).items())
                .extracting(Author::getName).containsExactly("Gabriel García Márquez");
        assertThat(adapter.findAll("GARCÍA", true, PageQuery.of(0, 20, "name")).items())
                .extracting(Author::getName).containsExactly("Gabriel García Márquez");
        assertThat(adapter.findAll("josé", true, PageQuery.of(0, 20, "name")).items())
                .extracting(Author::getName).containsExactly("Jose Saramago");
        assertThat(adapter.findAll("joze", true, PageQuery.of(0, 20, "name")).items()).isEmpty();
    }

    @Test
    void theWildcardsOfTheNameShouldStayLiteral() {
        adapter.save(Author.create("Ana 100%"));
        adapter.save(Author.create("Ana 1000"));

        assertThat(adapter.findAll("100%", true, PageQuery.of(0, 20, "name")).items())
                .extracting(Author::getName).containsExactly("Ana 100%");
        assertThat(adapter.findAll("ana_", true, PageQuery.of(0, 20, "name")).items()).isEmpty();
    }

    @Test
    void theFilterByNameShouldKeepPaginatingAndRespectTheActiveFlag() {
        for (var i = 1; i <= 5; i++) {
            adapter.save(Author.create("Joaquim José " + i));
        }
        var inactive = Author.create("Joaquim José 6");
        inactive.deactivate();
        adapter.save(inactive);

        var firstPage = adapter.findAll("jose", true, PageQuery.of(0, 2, "name"));
        var lastPage = adapter.findAll("jose", true, PageQuery.of(2, 2, "name"));

        assertThat(firstPage.totalItems()).isEqualTo(5);
        assertThat(firstPage.items()).extracting(Author::getName).containsExactly("Joaquim José 1", "Joaquim José 2");
        assertThat(lastPage.items()).extracting(Author::getName).containsExactly("Joaquim José 5");
        assertThat(adapter.findAll("jose", false, PageQuery.of(0, 20, "name")).items())
                .extracting(Author::getName).containsExactly("Joaquim José 6");
    }
}
