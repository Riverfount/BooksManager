package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansUseCase;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the "no query per loan" criterion of the loan listing (RF14, RNF14): the whole use
 * case, with the real adapters and PostgreSQL, runs a fixed number of queries per page (the
 * page, its total count, and the fines and suspensions of the whole page), however many loans
 * the page has and however many of them have a penalty.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class LoanQueryCountTest {

    private static final int QUERIES_PER_PAGE = 4;
    private static final LocalDate PICKUP = LocalDate.of(2026, 1, 1);

    @Autowired
    private ListLoansUseCase listLoans;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private long bookId;
    private long readerId;
    private int created;

    @BeforeEach
    void createParents() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Loan query count') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000071', 'T', ?) returning id",
                Long.class, categoryId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'LQC-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    @Test
    void listingShouldRunAFixedNumberOfQueriesWhateverThePageSize() {
        createReturnedLoansWithPenalty(4);
        var smallPage = queriesFor(new ListLoansQuery(readerId, null, null, PageQuery.of(0, 2, "id")));

        createReturnedLoansWithPenalty(46);
        var bigPage = queriesFor(new ListLoansQuery(readerId, null, null, PageQuery.of(0, 50, "id")));

        assertThat(bigPage).isEqualTo(smallPage).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    private long queriesFor(ListLoansQuery query) {
        // the persistence context must be empty, or cached entities would hide queries
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = listLoans.list(query);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allMatch(details -> details.fine() != null && details.suspension() != null);
        return statistics.getPrepareStatementCount();
    }

    private void createReturnedLoansWithPenalty(int quantity) {
        for (var i = 0; i < quantity; i++) {
            var number = created++;
            var copyId = jdbcTemplate.queryForObject(
                    "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                    Long.class, bookId, "LQC-" + number);
            var loanId = jdbcTemplate.queryForObject("""
                    insert into loan (copy_id, reader_id, pickup_date, due_date, return_date)
                    values (?, ?, ?, ?, ?) returning id
                    """, Long.class, copyId, readerId, PICKUP, PICKUP.plusDays(14), PICKUP.plusDays(20));
            jdbcTemplate.update("insert into fine (loan_id, reader_id, amount, status) values (?, ?, 6.00, 'PENDING')",
                    loanId, readerId);
            jdbcTemplate.update("""
                    insert into suspension (reader_id, loan_id, start_date, end_date) values (?, ?, ?, ?)
                    """, readerId, loanId, PICKUP.plusDays(20), PICKUP.plusDays(26));
        }
    }
}
