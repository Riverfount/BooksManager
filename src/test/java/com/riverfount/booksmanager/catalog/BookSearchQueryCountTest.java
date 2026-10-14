package com.riverfount.booksmanager.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksUseCase;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the "no query per book" criterion of the book search (RF05): the
 * whole use case, with the real adapters and PostgreSQL, runs a fixed
 * number of queries per page, however many books the page has. Those are
 * the page, its total count, the authors of the whole page in one batch
 * and the available copies of the whole page in one aggregated query. A
 * regression to one query per book would make the second run below fire
 * far more statements than the first.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class BookSearchQueryCountTest {

    private static final int QUERIES_PER_PAGE = 4;

    @Autowired
    private ListBooksUseCase listBooksUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long categoryId;
    private Long authorId1;
    private Long authorId2;
    private int booksCreated;

    @BeforeEach
    void setUp() {
        categoryId = insert("insert into category (name, active) values ('Ficção', true) returning id");
        authorId1 = insert("insert into author (name, active) values ('Autor Um', true) returning id");
        authorId2 = insert("insert into author (name, active) values ('Autor Dois', true) returning id");
    }

    @Test
    void plainListingShouldRunAFixedNumberOfQueriesWhateverThePageSize() {
        createBooks(4);
        var smallPage = queriesFor(new ListBooksQuery(true, PageQuery.of(0, 2, "title")));

        createBooks(56);
        var bigPage = queriesFor(new ListBooksQuery(true, PageQuery.of(0, 50, "title")));

        assertThat(bigPage).isEqualTo(smallPage).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    @Test
    void searchWithAllTheFiltersShouldRunAFixedNumberOfQueriesWhateverThePageSize() {
        createBooks(4);
        var filters = new ListBooksQuery("livro", "autor", "978", categoryId, true,
                PageQuery.of(0, 2, "title"));
        var smallPage = queriesFor(filters);

        createBooks(56);
        var bigPage = queriesFor(new ListBooksQuery("livro", "autor", "978", categoryId, true,
                PageQuery.of(0, 50, "title")));

        assertThat(bigPage).isEqualTo(smallPage).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    @Test
    void shouldAlsoReturnTheRightAvailableCopiesWhileKeepingTheQueriesFixed() {
        createBooks(60);
        entityManager.clear();

        var page = listBooksUseCase.list(new ListBooksQuery(true, PageQuery.of(0, 50, "title")));

        assertThat(page.items()).hasSize(50)
                .allSatisfy(item -> assertThat(item.availableCopies()).isEqualTo(1L));
        assertThat(page.totalItems()).isEqualTo(60);
    }

    private long queriesFor(ListBooksQuery query) {
        // the persistence context must be empty, or cached entities would hide queries
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = listBooksUseCase.list(query);

        assertThat(page.items()).isNotEmpty()
                .allSatisfy(item -> assertThat(item.book().getAuthorIds()).hasSize(2));
        return statistics.getPrepareStatementCount();
    }

    /** Each book has both authors, one available copy and one borrowed copy. */
    private void createBooks(int quantity) {
        for (var i = 0; i < quantity; i++) {
            var number = booksCreated++;
            var bookId = insert("insert into book (isbn, title, category_id, active) values ('"
                    + isbn13(number) + "', 'Livro " + number + "', " + categoryId + ", true) returning id");
            jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?), (?, ?)",
                    bookId, authorId1, bookId, authorId2);
            jdbcTemplate.update("insert into copy (book_id, asset_code, status, active, version) "
                    + "values (?, ?, 'AVAILABLE', true, 0), (?, ?, 'BORROWED', true, 0)",
                    bookId, "PAT-" + number + "-A", bookId, "PAT-" + number + "-B");
        }
    }

    private Long insert(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    /** A valid ISBN-13 (978 + 9 digits + check digit), since the domain validates the ISBN. */
    private static String isbn13(int number) {
        var body = String.format("978%09d", number);
        var sum = 0;
        for (var i = 0; i < body.length(); i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return body + ((10 - sum % 10) % 10);
    }
}
