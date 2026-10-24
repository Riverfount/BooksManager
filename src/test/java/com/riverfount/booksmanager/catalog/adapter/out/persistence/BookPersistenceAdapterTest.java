package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.catalog.application.port.out.BookFilter;
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
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the Book persistence adapter against a real PostgreSQL
 * (Testcontainers), including the N:N author association mapped as a
 * collection of IDs (book_author).
 */
@PersistenceTest
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
    void deactivatedBookShouldStayInTheDatabaseWithItsAuthors() {
        var saved = adapter.save(Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899,
                categoryId, Set.of(authorId1, authorId2), FIXED_CLOCK));
        entityManager.flush();
        entityManager.clear();

        var book = adapter.findById(saved.getId()).orElseThrow();
        book.deactivate();
        adapter.save(book);
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForObject("select active from book where id = ?", Boolean.class,
                saved.getId())).isFalse();
        assertThat(adapter.findById(saved.getId()).orElseThrow().getAuthorIds())
                .containsExactlyInAnyOrder(authorId1, authorId2);
    }

    @Test
    void shouldFilterByTitleContainingIgnoringCase() {
        saveBook("9788533302273", "Dom Casmurro", categoryId, Set.of(authorId1));
        saveBook("9788535914849", "Memórias Póstumas", categoryId, Set.of(authorId1));

        var result = adapter.findAll(new BookFilter("CASMUR", null, null, null), true, byTitle());

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Dom Casmurro");
        assertThat(result.totalItems()).isEqualTo(1);
    }

    @Test
    void shouldFilterByAuthorNameContainingIgnoringCase() {
        var machado = newAuthor("Machado de Assis");
        var clarice = newAuthor("Clarice Lispector");
        saveBook("9788533302273", "Dom Casmurro", categoryId, Set.of(machado));
        saveBook("9788535914849", "A Hora da Estrela", categoryId, Set.of(clarice));

        var result = adapter.findAll(new BookFilter(null, "MACHADO", null, null), true, byTitle());

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldFindTheBookByAnyOfItsAuthors() {
        var machado = newAuthor("Machado de Assis");
        var clarice = newAuthor("Clarice Lispector");
        saveBook("9788533302273", "Livro a Quatro Mãos", categoryId, Set.of(machado, clarice));

        assertThat(adapter.findAll(new BookFilter(null, "machado", null, null), true, byTitle()).items())
                .extracting(Book::getTitle).containsExactly("Livro a Quatro Mãos");
        assertThat(adapter.findAll(new BookFilter(null, "clarice", null, null), true, byTitle()).items())
                .extracting(Book::getTitle).containsExactly("Livro a Quatro Mãos");
    }

    @Test
    void aBookWithTwoMatchingAuthorsShouldAppearAndCountOnlyOnce() {
        saveBook("9788533302273", "Dom Casmurro", categoryId, Set.of(authorId1, authorId2));

        var result = adapter.findAll(new BookFilter(null, "Autor", null, null), true, byTitle());

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalItems()).isEqualTo(1);
    }

    @Test
    void shouldFilterByPartOfTheIsbn() {
        saveBook("9788533302273", "Dom Casmurro", categoryId, Set.of(authorId1));
        saveBook("9788535914849", "Memórias Póstumas", categoryId, Set.of(authorId1));

        var result = adapter.findAll(new BookFilter(null, null, "978-85-3330", null), true, byTitle());

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldStoreAnIsbn10AsTheIsbn13OfTheSameBook() {
        saveBook("080442957X", "Livro ISBN-10", categoryId, Set.of(authorId1));

        assertThat(jdbcTemplate.queryForObject("select isbn from book", String.class)).isEqualTo("9780804429573");
        assertThat(adapter.existsByIsbn(new Isbn("9780804429573"))).isTrue();
        assertThat(adapter.existsByIsbn(new Isbn("080442957X"))).isTrue();
        assertThat(adapter.findIdByIsbn(new Isbn("0-8044-2957-X"))).isPresent();
    }

    @Test
    void shouldFindAnIsbn10ByTheDigitsThatItSharesWithTheIsbn13() {
        saveBook("080442957X", "Livro ISBN-10", categoryId, Set.of(authorId1));

        var result = adapter.findAll(new BookFilter(null, null, "0442957", null), true, byTitle());

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Livro ISBN-10");
    }

    @Test
    void theDatabaseShouldRefuseAnIsbnThatIsNotAnIsbn13() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into book (isbn, title, category_id) values ('080442957X', 'Livro', ?)", categoryId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("book_isbn_13_digits");
    }

    @Test
    void shouldFilterByCategory() {
        var romance = newCategory("Romance");
        saveBook("9788533302273", "Dom Casmurro", romance, Set.of(authorId1));
        saveBook("9788535914849", "Livro de Ficção", categoryId, Set.of(authorId1));

        var result = adapter.findAll(new BookFilter(null, null, null, romance), true, byTitle());

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldCombineAllTheFiltersWithAnd() {
        var romance = newCategory("Romance");
        var machado = newAuthor("Machado de Assis");
        saveBook("9788533302273", "Dom Casmurro", romance, Set.of(machado));
        saveBook("9788535914849", "Dom Quixote", romance, Set.of(authorId1));
        saveBook("9788525406958", "Dom Alheio", categoryId, Set.of(machado));

        var result = adapter.findAll(new BookFilter("dom", "machado", "9788533", romance), true, byTitle());

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldKeepFilteringByTheActiveFlag() {
        saveBook("9788533302273", "Dom Casmurro", categoryId, Set.of(authorId1));
        save("9788535914849", "Dom Inativo", 2000, false);

        assertThat(adapter.findAll(new BookFilter("dom", null, null, null), true, byTitle()).items())
                .extracting(Book::getTitle).containsExactly("Dom Casmurro");
        assertThat(adapter.findAll(new BookFilter("dom", null, null, null), false, byTitle()).items())
                .extracting(Book::getTitle).containsExactly("Dom Inativo");
    }

    @Test
    void shouldTreatLikeWildcardsInTheTitleFilterLiterally() {
        saveBook("9788533302273", "100% Java", categoryId, Set.of(authorId1));
        saveBook("9788535914849", "100 Java", categoryId, Set.of(authorId1));
        saveBook("9788525406958", "A_B", categoryId, Set.of(authorId1));
        saveBook("9788532530806", "AxB", categoryId, Set.of(authorId1));

        assertThat(adapter.findAll(new BookFilter("%", null, null, null), true, byTitle()).items())
                .extracting(Book::getTitle).containsExactly("100% Java");
        assertThat(adapter.findAll(new BookFilter("_", null, null, null), true, byTitle()).items())
                .extracting(Book::getTitle).containsExactly("A_B");
    }

    @Test
    void shouldTreatLikeWildcardsInTheAuthorFilterLiterally() {
        var literal = newAuthor("Autor_Especial");
        var other = newAuthor("AutorXEspecial");
        saveBook("9788533302273", "Livro Literal", categoryId, Set.of(literal));
        saveBook("9788535914849", "Livro Outro", categoryId, Set.of(other));

        var result = adapter.findAll(new BookFilter(null, "_", null, null), true, byTitle());

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Livro Literal");
    }

    @Test
    void shouldPaginateAFilteredSearch() {
        saveBook("9788533302273", "Dom A", categoryId, Set.of(authorId1));
        saveBook("9788535914849", "Dom B", categoryId, Set.of(authorId1));
        saveBook("9788525406958", "Dom C", categoryId, Set.of(authorId1));
        saveBook("9788532530806", "Outro", categoryId, Set.of(authorId1));

        var page = adapter.findAll(new BookFilter("dom", null, null, null), true, PageQuery.of(1, 2, "title"));

        assertThat(page.items()).extracting(Book::getTitle).containsExactly("Dom C");
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldReturnAnEmptyPageWhenNothingMatches() {
        saveBook("9788533302273", "Dom Casmurro", categoryId, Set.of(authorId1));

        var result = adapter.findAll(new BookFilter("inexistente", null, null, null), true, byTitle());

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
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
    void findIdByIsbnShouldGiveTheIdOfTheBookEvenWhenItIsInactive() {
        var saved = adapter.save(Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoryId,
                Set.of(authorId1), FIXED_CLOCK));
        saved.deactivate();
        adapter.save(saved);

        assertThat(adapter.findIdByIsbn(new Isbn("9788533302273"))).contains(saved.getId());
        assertThat(adapter.findIdByIsbn(new Isbn("080442957X"))).isEmpty();
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

        var firstPage = adapter.findAll(BookFilter.none(), true, PageQuery.of(0, 2, "title"));
        var secondPage = adapter.findAll(BookFilter.none(), true, PageQuery.of(1, 2, "title"));

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

        var result = adapter.findAll(BookFilter.none(), false, PageQuery.of(0, 20, "title"));

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Livro Inativo");
    }

    @Test
    void shouldSortByPublicationYearDescendingAndByIsbn() {
        save("9788535914849", "Mais Antigo", 1899, true);
        save("9788533302273", "Mais Novo", 2010, true);

        assertThat(adapter.findAll(BookFilter.none(), true, PageQuery.of(0, 20, "publicationYear,desc")).items())
                .extracting(Book::getTitle).containsExactly("Mais Novo", "Mais Antigo");
        assertThat(adapter.findAll(BookFilter.none(), true, PageQuery.of(0, 20, "isbn")).items())
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

        var result = adapter.findAll(BookFilter.none(), true, PageQuery.of(0, 20, "title"));

        assertThat(result.items()).hasSize(isbns.length)
                .allSatisfy(book -> assertThat(book.getAuthorIds()).hasSize(2));
        // one query for the page and one batch for all the authors
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    private Long saveBook(String isbn, String title, Long category, Set<Long> authors) {
        return adapter.save(Book.create(new Isbn(isbn), title, "Editora X", 2000, category, authors,
                FIXED_CLOCK)).getId();
    }

    private Long newAuthor(String name) {
        return jdbcTemplate.queryForObject("insert into author (name, active) values (?, true) returning id",
                Long.class, name);
    }

    private Long newCategory(String name) {
        return jdbcTemplate.queryForObject("insert into category (name, active) values (?, true) returning id",
                Long.class, name);
    }

    private static PageQuery byTitle() {
        return PageQuery.of(0, 20, "title");
    }

    private void save(String isbn, String title, Integer publicationYear, boolean active) {
        var book = Book.create(new Isbn(isbn), title, "Editora X", publicationYear, categoryId,
                Set.of(authorId1, authorId2), FIXED_CLOCK);
        if (!active) {
            book.deactivate();
        }
        adapter.save(book);
    }


    @Test
    void shouldFindTheTitlesOfSeveralBooksInOneCall() {
        var first = adapter.save(Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899,
                categoryId, Set.of(authorId1, authorId2), FIXED_CLOCK));
        var second = adapter.save(Book.create(new Isbn("080442957X"), "Quincas Borba", "Editora X", 1891,
                categoryId, Set.of(authorId1), FIXED_CLOCK));

        var titles = adapter.findTitlesByIds(Set.of(first.getId(), second.getId(), -1L));

        assertThat(titles).containsOnlyKeys(first.getId(), second.getId());
        assertThat(titles.get(first.getId())).isEqualTo("Dom Casmurro");
        assertThat(titles.get(second.getId())).isEqualTo("Quincas Borba");
    }

    @Test
    void shouldFindNoTitlesForNoIds() {
        assertThat(adapter.findTitlesByIds(Set.of())).isEmpty();
    }
}
