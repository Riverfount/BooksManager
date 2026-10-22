package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksUseCase;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the "fixed number of queries" criterion of the most borrowed books report (RF24, RNF14, RNF15): the
 * whole use case, with the real adapters of the two modules and PostgreSQL, runs the same few queries (the loans
 * counted per copy, then the copies and their books' titles) whatever the number of loans, copies and books in the
 * period, and whatever the size of the page.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MostBorrowedBooksQueryCountTest {

    private static final int QUERIES = 3;
    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired
    private ListMostBorrowedBooksUseCase report;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private long categoryId;
    private long readerId;
    private int created;

    @BeforeEach
    void createParents() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Most borrowed query count') returning id", Long.class);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'MQC-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    @Test
    void theReportShouldRunAFixedNumberOfQueriesWhateverTheNumberOfBooksAndLoans() {
        createBooksWithLoans(4);
        var small = queriesFor(ListMostBorrowedBooksQuery.of(FROM, TO, 0, 2));

        createBooksWithLoans(46);
        var big = queriesFor(ListMostBorrowedBooksQuery.of(FROM, TO, 0, 50));

        assertThat(big).isEqualTo(small).isLessThanOrEqualTo(QUERIES);
    }

    private long queriesFor(ListMostBorrowedBooksQuery query) {
        // the persistence context must be empty, or cached entities would hide queries
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = report.list(query);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allMatch(book -> book.title() != null && book.totalLoans() > 0);
        return statistics.getPrepareStatementCount();
    }

    /** The 12 digits plus the ISBN-13 check digit: the catalog refuses a book whose ISBN does not check out. */
    private static String isbn(String twelveDigits) {
        var sum = 0;
        for (var i = 0; i < 12; i++) {
            sum += (twelveDigits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return twelveDigits + (10 - sum % 10) % 10;
    }

    /** Each book gets two copies, and each copy a returned loan in the period. */
    private void createBooksWithLoans(int quantity) {
        for (var i = 0; i < quantity; i++) {
            var number = created++;
            var bookId = jdbcTemplate.queryForObject(
                    "insert into book (isbn, title, category_id) values (?, ?, ?) returning id", Long.class,
                    isbn("978000009" + String.format("%03d", number)), "Title " + number, categoryId);
            for (var copy = 0; copy < 2; copy++) {
                var copyId = jdbcTemplate.queryForObject(
                        "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                        Long.class, bookId, "MQC-" + number + "-" + copy);
                jdbcTemplate.update("""
                        insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                        values (?, ?, ?, ?, ?, 'GOOD')
                        """, copyId, readerId, FROM.plusDays(copy), FROM.plusDays(14), FROM.plusDays(copy + 1));
            }
        }
    }
}
