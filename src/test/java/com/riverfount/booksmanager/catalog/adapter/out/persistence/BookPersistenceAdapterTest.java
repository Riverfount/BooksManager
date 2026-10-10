package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the Book persistence adapter against a real PostgreSQL
 * (Testcontainers), including the N:N author association mapped as a
 * collection of IDs (book_author).
 */
@Import({TestcontainersConfiguration.class, BookPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class BookPersistenceAdapterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private BookPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    private Long categoryId;
    private Long authorId1;
    private Long authorId2;

    @BeforeEach
    void setUp() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name, active) values ('Ficção', true) returning id", Long.class);
        authorId1 = jdbcTemplate.queryForObject(
                "insert into author (name, active) values ('Autor Um', true) returning id", Long.class);
        authorId2 = jdbcTemplate.queryForObject(
                "insert into author (name, active) values ('Autor Dois', true) returning id", Long.class);
    }

    @Test
    void shouldSaveAndFindBookWithTheSameState() {
        var book = Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoryId,
                Set.of(authorId1, authorId2), FIXED_CLOCK);

        var saved = adapter.save(book);
        var found = adapter.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getIsbn()).isEqualTo(new Isbn("9788533302273"));
        assertThat(found.get().getTitle()).isEqualTo("Dom Casmurro");
        assertThat(found.get().getPublisher()).isEqualTo("Editora X");
        assertThat(found.get().getPublicationYear()).isEqualTo(1899);
        assertThat(found.get().getCategoryId()).isEqualTo(categoryId);
        assertThat(found.get().getAuthorIds()).containsExactlyInAnyOrder(authorId1, authorId2);
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void shouldWriteTwoRowsInBookAuthorWhenSavingBookWithTwoAuthors() {
        var book = Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoryId,
                Set.of(authorId1, authorId2), FIXED_CLOCK);

        var saved = adapter.save(book);
        entityManager.flush();

        var totalRows = jdbcTemplate.queryForObject(
                "select count(*) from book_author where book_id = ?", Integer.class, saved.getId());
        assertThat(totalRows).isEqualTo(2);
    }

    @Test
    void foreignKeyViolationShouldNotBeConfusedWithDuplicateIsbn() {
        var bookWithNonExistentCategory = Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899,
                999999L, Set.of(authorId1), FIXED_CLOCK);

        assertThatThrownBy(() -> adapter.save(bookWithNonExistentCategory))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duplicateIsbnShouldViolateTheUniqueConstraintAndBecomeBusinessRuleException() {
        adapter.save(Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoryId,
                Set.of(authorId1), FIXED_CLOCK));

        assertThatThrownBy(() -> adapter.save(Book.create(new Isbn("9788533302273"), "Outro Livro", "Editora Y",
                1900, categoryId, Set.of(authorId2), FIXED_CLOCK)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void existsByIsbnShouldReflectWhatHasAlreadyBeenSaved() {
        adapter.save(Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoryId,
                Set.of(authorId1), FIXED_CLOCK));

        assertThat(adapter.existsByIsbn(new Isbn("9788533302273"))).isTrue();
        assertThat(adapter.existsByIsbn(new Isbn("080442957X"))).isFalse();
    }

    @Test
    void shouldReturnEmptyForNonExistentId() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }
}
