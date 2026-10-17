package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.application.port.out.CategoryCopyCount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The catalog counts for the dashboard (RF25), against a real PostgreSQL: the active books and the active
 * copies of each category, in one query, with the inactive ones left out and a category with nothing at zero.
 */
@Import({TestcontainersConfiguration.class, CatalogSummaryPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CatalogSummaryPersistenceAdapterTest {

    private static int isbnSequence = 100;

    @Autowired
    private CatalogSummaryPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long category(String name, boolean active) {
        return jdbcTemplate.queryForObject("insert into category (name, active) values (?, ?) returning id",
                Long.class, name, active);
    }

    private long book(long categoryId, boolean active) {
        var isbn = "9780000" + String.format("%06d", isbnSequence++);
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id, active) values (?, 'T', ?, ?) returning id",
                Long.class, isbn, categoryId, active);
    }

    private void copy(long bookId, boolean active, String assetCode) {
        jdbcTemplate.update("insert into copy (book_id, asset_code, status, active) values (?, ?, 'AVAILABLE', ?)",
                bookId, assetCode, active);
    }

    private CategoryCopyCount row(String name) {
        return adapter.countByCategory().stream().filter(row -> row.categoryName().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    void shouldCountTheActiveBooksAndTheActiveCopiesOfEachCategory() {
        var fiction = category("CSA Ficção", true);
        var poetry = category("CSA Poesia", true);
        var novel = book(fiction, true);
        var tale = book(fiction, true);
        var poems = book(poetry, true);
        copy(novel, true, "CSA-1");
        copy(novel, true, "CSA-2");
        copy(tale, true, "CSA-3");
        copy(poems, true, "CSA-4");

        assertThat(row("CSA Ficção")).extracting(CategoryCopyCount::books, CategoryCopyCount::copies)
                .containsExactly(2L, 3L);
        assertThat(row("CSA Poesia")).extracting(CategoryCopyCount::books, CategoryCopyCount::copies)
                .containsExactly(1L, 1L);
    }

    @Test
    void aCategoryWithoutBooksShouldCountZero() {
        category("CSA Vazia", true);

        assertThat(row("CSA Vazia")).extracting(CategoryCopyCount::books, CategoryCopyCount::copies)
                .containsExactly(0L, 0L);
    }

    @Test
    void anInactiveCopyShouldNotCountAndAnInactiveBookShouldNotCountAsABook() {
        var category = category("CSA Mista", true);
        var active = book(category, true);
        var inactive = book(category, false);
        copy(active, true, "CSA-10");
        copy(active, false, "CSA-11");
        // the copies of an inactive book are still active copies of the library: they count, the book does not
        copy(inactive, true, "CSA-12");

        assertThat(row("CSA Mista")).extracting(CategoryCopyCount::books, CategoryCopyCount::copies)
                .containsExactly(1L, 2L);
    }

    @Test
    void shouldSayIfTheCategoryIsActive() {
        category("CSA Ativa", true);
        category("CSA Antiga", false);

        assertThat(row("CSA Ativa").categoryActive()).isTrue();
        assertThat(row("CSA Antiga").categoryActive()).isFalse();
        assertThat(row("CSA Antiga").categoryId()).isNotNull();
    }

    @Test
    void shouldComeOrderedByNameWithoutCaringAboutCase() {
        category("csa b-minúscula", true);
        category("CSA A-maiúscula", true);
        category("CSA C-maiúscula", true);

        var names = adapter.countByCategory().stream().map(CategoryCopyCount::categoryName)
                .filter(name -> name.startsWith("csa") || name.startsWith("CSA")).toList();

        assertThat(names).containsExactly("CSA A-maiúscula", "csa b-minúscula", "CSA C-maiúscula");
    }
}
