package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the Category persistence adapter against a real PostgreSQL
 * (Testcontainers). The context starting up already proves that
 * CategoryJpaEntity matches the table created by V1
 * (ddl-auto=validate).
 */
@Import({TestcontainersConfiguration.class, CategoryPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CategoryPersistenceAdapterTest {

    @Autowired
    private CategoryPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindCategoryWithTheSameState() {
        var saved = adapter.save(Category.create("Ficção"));

        var found = adapter.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getName()).isEqualTo("Ficção");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void shouldFindManyCategoriesByIdIncludingInactiveOnesInOneCall() {
        var active = adapter.save(Category.create("Ficção"));
        var inactive = adapter.save(Category.reconstitute(null, "Poesia", false));

        var found = adapter.findAllByIds(java.util.Set.of(active.getId(), inactive.getId(), 999999L));

        assertThat(found).containsOnlyKeys(active.getId(), inactive.getId());
        assertThat(found.get(inactive.getId()).getName()).isEqualTo("Poesia");
        assertThat(found.get(inactive.getId()).isActive()).isFalse();
        assertThat(adapter.findAllByIds(java.util.Set.of())).isEmpty();
    }

    @Test
    void shouldPersistDeactivationKeepingTheRowAndTheBooksThatUseIt() {
        // RN14: deactivating is an update, never a physical delete
        var category = adapter.save(Category.create("Romance"));
        var bookId = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, publisher, publication_year, category_id, active)
                values ('9788533302273', 'Dom Casmurro', 'Editora X', 1899, ?, true) returning id
                """, Long.class, category.getId());

        category.deactivate();
        adapter.save(category);

        var found = adapter.findById(category.getId());
        assertThat(found).isPresent();
        assertThat(found.get().isActive()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select category_id from book where id = ?", Long.class, bookId))
                .isEqualTo(category.getId());
    }

    @Test
    void shouldReturnEmptyForNonExistentId() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }

    @Test
    void existsByNameShouldReflectWhatHasAlreadyBeenSaved() {
        adapter.save(Category.create("Suspense"));

        assertThat(adapter.existsByName("Suspense")).isTrue();
        assertThat(adapter.existsByName("Nome inexistente")).isFalse();
    }

    @Test
    void shouldTranslateTheUniqueConstraintViolationIntoBusinessRuleException() {
        // simulates the race: two calls to save() without going through
        // Category.validateUniqueName's check (which is the service's
        // job, not the adapter's) — the port must never let the raw
        // persistence exception escape.
        adapter.save(Category.create("Terror"));

        assertThatThrownBy(() -> adapter.save(Category.create("Terror")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldTranslateTheUniqueConstraintViolationOnRenameIntoBusinessRuleException() {
        // same race as above, but on update: the UPDATE must reach the
        // database inside save(), otherwise the violation would only show
        // up at commit time, outside the translation
        adapter.save(Category.create("Terror"));
        var other = adapter.save(Category.create("Suspense"));
        other.rename("Terror");

        assertThatThrownBy(() -> adapter.save(other))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldUpdateExistingCategoryInsteadOfInsertingANewOne() {
        var saved = adapter.save(Category.create("Ficcão"));
        saved.rename("Ficção");

        var updated = adapter.save(saved);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(adapter.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Ficção");
        assertThat(adapter.findAll(null, true, PageQuery.of(0, 20, "name")).totalItems()).isEqualTo(1);
    }

    @Test
    void existsByNameAndIdNotShouldIgnoreTheCategoryItself() {
        var fiction = adapter.save(Category.create("Ficção"));
        var romance = adapter.save(Category.create("Romance"));

        assertThat(adapter.existsByNameAndIdNot("Ficção", fiction.getId())).isFalse();
        assertThat(adapter.existsByNameAndIdNot("Ficção", romance.getId())).isTrue();
        assertThat(adapter.existsByNameAndIdNot("Nome inexistente", romance.getId())).isFalse();
    }

    @Test
    void shouldListActiveCategoriesPaginatedAndSortedByName() {
        adapter.save(Category.create("Categoria C"));
        adapter.save(Category.create("Categoria A"));
        adapter.save(Category.create("Categoria B"));
        adapter.save(Category.reconstitute(null, "Categoria Inativa", false));

        var firstPage = adapter.findAll(null, true, PageQuery.of(0, 2, "name"));
        var secondPage = adapter.findAll(null, true, PageQuery.of(1, 2, "name"));

        assertThat(firstPage.items()).extracting(Category::getName).containsExactly("Categoria A", "Categoria B");
        assertThat(secondPage.items()).extracting(Category::getName).containsExactly("Categoria C");
        assertThat(firstPage.totalItems()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldListInactiveCategoriesWhenRequested() {
        adapter.save(Category.create("Categoria Ativa"));
        adapter.save(Category.reconstitute(null, "Categoria Inativa", false));

        var result = adapter.findAll(null, false, PageQuery.of(0, 20, "name"));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Categoria Inativa");
    }

    @Test
    void shouldFilterByNameContainingIgnoringCase() {
        adapter.save(Category.create("Ficção Científica"));
        adapter.save(Category.create("Romance"));

        var result = adapter.findAll("CIENT", true, PageQuery.of(0, 20, "name"));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Ficção Científica");
    }

    @Test
    void shouldTreatLikeWildcardsInNameFilterLiterally() {
        adapter.save(Category.create("Infantil_Juvenil"));
        adapter.save(Category.create("InfantilXJuvenil"));

        var result = adapter.findAll("_", true, PageQuery.of(0, 20, "name"));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Infantil_Juvenil");
    }

    @Test
    void shouldSortByIdDescending() {
        var first = adapter.save(Category.create("Categoria A"));
        var second = adapter.save(Category.create("Categoria B"));

        var result = adapter.findAll(null, true, PageQuery.of(0, 20, "id,desc"));

        assertThat(result.items()).extracting(Category::getId).containsExactly(second.getId(), first.getId());
    }
}
