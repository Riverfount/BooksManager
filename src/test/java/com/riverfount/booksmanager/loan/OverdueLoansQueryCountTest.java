package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansUseCase;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the "no query per loan" criterion of the overdue loans report (RF23, RNF14, RNF15): the
 * whole use case, with the real adapters of the three modules and PostgreSQL, runs a fixed number of
 * queries per page (the page, its total count, the readers' names and the copies and book titles of
 * the whole page), however many loans the page has.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class OverdueLoansQueryCountTest {

    private static final int QUERIES_PER_PAGE = 5;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private ListOverdueLoansUseCase report;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private long bookId;
    private int created;

    @BeforeEach
    void createABook() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Overdue query count') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000156', 'T', ?) returning id",
                Long.class, categoryId);
    }

    @Test
    void theReportShouldRunAFixedNumberOfQueriesWhateverThePageSize() {
        createLateLoans(4);
        var smallPage = queriesFor(ListOverdueLoansQuery.of(0, 2));

        createLateLoans(46);
        var bigPage = queriesFor(ListOverdueLoansQuery.of(0, 50));

        assertThat(bigPage).isEqualTo(smallPage).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    private long queriesFor(ListOverdueLoansQuery query) {
        // the persistence context must be empty, or cached entities would hide queries
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = report.list(query);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allMatch(item -> item.readerName() != null && item.copy() != null
                && item.copy().bookTitle() != null);
        return statistics.getPrepareStatementCount();
    }

    private void createLateLoans(int quantity) {
        for (var i = 0; i < quantity; i++) {
            var number = created++;
            var readerId = jdbcTemplate.queryForObject("""
                    insert into reader (name, document, email, birth_date)
                    values (?, ?, 'r@example.com', date '1990-01-01') returning id
                    """, Long.class, "Reader " + number, "OQC-DOC-" + number);
            var copyId = jdbcTemplate.queryForObject(
                    "insert into copy (book_id, asset_code, status) values (?, ?, 'BORROWED') returning id",
                    Long.class, bookId, "OQC-" + number);
            var due = TODAY.minusDays(1 + number);
            jdbcTemplate.update("insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?)",
                    copyId, readerId, due.minusDays(14), due);
        }
    }
}
