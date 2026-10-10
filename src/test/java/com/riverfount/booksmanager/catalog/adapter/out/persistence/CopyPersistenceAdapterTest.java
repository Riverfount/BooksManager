package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
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
 * Tests the Copy persistence adapter against a real PostgreSQL
 * (Testcontainers), including the actual optimistic lock (RNF17), via
 * {@code @Version} on the JPA entity.
 */
@Import({TestcontainersConfiguration.class, CopyPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class CopyPersistenceAdapterTest {

    @Autowired
    private CopyPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long bookId;

    @BeforeEach
    void setUp() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name, active) values ('Ficção', true) returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                """
                insert into book (isbn, title, category_id, active)
                values ('9788533302273', 'Dom Casmurro', ?, true) returning id
                """,
                Long.class, categoryId);
    }

    @Test
    void shouldSaveAndFindCopyWithTheSameState() {
        var saved = adapter.save(Copy.create(bookId, "PAT-1"));

        var found = adapter.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getBookId()).isEqualTo(bookId);
        assertThat(found.get().getAssetCode()).isEqualTo("PAT-1");
        assertThat(found.get().getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(found.get().isActive()).isTrue();
        assertThat(found.get().getVersion()).isEqualTo(0L);
    }

    @Test
    void shouldReturnEmptyForNonExistentId() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }

    @Test
    void duplicateAssetCodeShouldViolateTheUniqueConstraintAndBecomeBusinessRuleException() {
        adapter.save(Copy.create(bookId, "PAT-1"));

        assertThatThrownBy(() -> adapter.save(Copy.create(bookId, "PAT-1")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void foreignKeyViolationShouldNotBeConfusedWithDuplicateAssetCode() {
        var copyWithNonExistentBook = Copy.create(999999L, "PAT-1");

        assertThatThrownBy(() -> adapter.save(copyWithNonExistentBook))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessRuleException.class);
    }

    @Test
    void existsByAssetCodeShouldReflectWhatHasAlreadyBeenSaved() {
        adapter.save(Copy.create(bookId, "PAT-1"));

        assertThat(adapter.existsByAssetCode("PAT-1")).isTrue();
        assertThat(adapter.existsByAssetCode("PAT-INEXISTENTE")).isFalse();
    }

    @Test
    void shouldKeepTheStatusWhenSavingAndFinding() {
        var saved = adapter.save(Copy.create(bookId, "PAT-1"));
        saved.checkOut();

        var updated = adapter.save(saved);
        var found = adapter.findById(updated.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(CopyStatus.BORROWED);
        assertThat(found.get().getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldFailWithObjectOptimisticLockingFailureExceptionWhenSavingTwoConcurrentCopies() {
        var original = adapter.save(Copy.create(bookId, "PAT-CONCORRENCIA"));

        // loads the same copy twice, simulating two concurrent requests
        // that read the same state (version 0)
        var copy1 = adapter.findById(original.getId()).orElseThrow();
        var copy2 = adapter.findById(original.getId()).orElseThrow();

        copy1.checkOut();
        copy2.checkOut();

        adapter.save(copy1); // success: version in the database becomes 1

        assertThatThrownBy(() -> adapter.save(copy2)) // copy2 still carries version 0
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
