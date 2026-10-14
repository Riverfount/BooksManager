package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.hibernate.SessionFactory;
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
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
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
    void shouldPersistEditedDetailsAndReplacedAuthorsOfAnExistingBook() {
        var saved = adapter.save(Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899,
                categoryId, Set.of(authorId1), FIXED_CLOCK));
        entityManager.flush();
        entityManager.clear();

        var book = adapter.findById(saved.getId()).orElseThrow();
        book.updateDetails("Dom Casmurro (revisado)", "Editora Y", 1900, categoryId, FIXED_CLOCK);
        book.replaceAuthors(Set.of(authorId2));
        adapter.save(book);
        entityManager.flush();
        entityManager.clear();

        var found = adapter.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Dom Casmurro (revisado)");
        assertThat(found.getPublisher()).isEqualTo("Editora Y");
        assertThat(found.getPublicationYear()).isEqualTo(1900);
        assertThat(found.getAuthorIds()).containsExactly(authorId2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from book_author where book_id = ?",
                Integer.class, saved.getId())).isEqualTo(1);
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

    @Test
    void shouldListActiveBooksPaginatedAndSortedByTitleWithTheirAuthors() {
        save("9788533302273", "Livro C", 1899, true);
        save("9788535914849", "Livro A", 2000, true);
        save("9788525406958", "Livro B", 2010, true);
        save("080442957X", "Livro Inativo", 1950, false);

        var firstPage = adapter.findAll(true, PageQuery.of(0, 2, "title"));
        var secondPage = adapter.findAll(true, PageQuery.of(1, 2, "title"));

        assertThat(firstPage.items()).extracting(Book::getTitle).containsExactly("Livro A", "Livro B");
        assertThat(secondPage.items()).extracting(Book::getTitle).containsExactly("Livro C");
        assertThat(firstPage.items().getFirst().getAuthorIds()).containsExactlyInAnyOrder(authorId1, authorId2);
        assertThat(firstPage.totalItems()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldListInactiveBooksWhenRequested() {
        save("9788533302273", "Livro Ativo", 1899, true);
        save("080442957X", "Livro Inativo", 1950, false);

        var result = adapter.findAll(false, PageQuery.of(0, 20, "title"));

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Livro Inativo");
    }

    @Test
    void shouldSortByPublicationYearDescendingAndByIsbn() {
        save("9788535914849", "Mais Antigo", 1899, true);
        save("9788533302273", "Mais Novo", 2010, true);

        assertThat(adapter.findAll(true, PageQuery.of(0, 20, "publicationYear,desc")).items())
                .extracting(Book::getTitle).containsExactly("Mais Novo", "Mais Antigo");
        assertThat(adapter.findAll(true, PageQuery.of(0, 20, "isbn")).items())
                .extracting(Book::getTitle).containsExactly("Mais Novo", "Mais Antigo");
    }

    @Test
    void shouldLoadTheAuthorsOfAPageWithoutOneQueryPerBook() {
        // avoids N+1: each book's authorIds come from book_author, a lazy
        // collection; the page must not fire one extra query per book
        var isbns = new String[] {"9788533302273", "9788535914849", "9788525406958", "080442957X", "8535902775"};
        for (var i = 0; i < isbns.length; i++) {
            save(isbns[i], "Livro " + i, 2000 + i, true);
        }
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var result = adapter.findAll(true, PageQuery.of(0, 20, "title"));

        assertThat(result.items()).hasSize(isbns.length)
                .allSatisfy(book -> assertThat(book.getAuthorIds()).hasSize(2));
        // one query for the page and one batch for all the authors
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    private void save(String isbn, String title, Integer publicationYear, boolean active) {
        var book = Book.create(new Isbn(isbn), title, "Editora X", publicationYear, categoryId,
                Set.of(authorId1, authorId2), FIXED_CLOCK);
        if (!active) {
            book.deactivate();
        }
        adapter.save(book);
    }
}
