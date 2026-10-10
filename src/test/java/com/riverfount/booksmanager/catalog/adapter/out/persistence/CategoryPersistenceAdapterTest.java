package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

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
}
