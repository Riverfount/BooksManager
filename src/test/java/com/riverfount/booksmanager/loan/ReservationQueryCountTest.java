package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsUseCase;
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
 * Guards the "no query per reservation" criterion of the reservation listing (RF16, RNF14): the whole use
 * case, with the real adapters and PostgreSQL, runs a fixed number of queries per page (the page, its total
 * count, the queue places of the whole page, the readers' names, the books' titles, and the copies' summaries,
 * which take two: the copies and their books), however many reservations the page has, waiting or holding a copy.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ReservationQueryCountTest {

    private static final int QUERIES_PER_PAGE = 7;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    @Autowired
    private ListReservationsUseCase listReservations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private long categoryId;
    private int created;

    @BeforeEach
    void createParents() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Reservation query count') returning id", Long.class);
    }

    @Test
    void listingShouldRunAFixedNumberOfQueriesWhateverThePageSize() {
        createReservations(4);
        var smallPage = queriesFor(PageQuery.of(0, 2, "id"));

        createReservations(46);
        var bigPage = queriesFor(PageQuery.of(0, 50, "id"));

        assertThat(bigPage).isEqualTo(smallPage).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    private long queriesFor(PageQuery pageQuery) {
        // the persistence context must be empty, or cached entities would hide queries
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = listReservations.list(new ListReservationsQuery(null, null, null, pageQuery));

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allMatch(details -> details.readerName() != null && details.bookTitle() != null
                && (details.queuePosition() != null ^ details.copy() != null));
        return statistics.getPrepareStatementCount();
    }

    /** Each one on a book of its own for a reader of its own: half waiting in the queue, half holding a copy. */
    private void createReservations(int quantity) {
        for (var i = 0; i < quantity; i++) {
            var number = created++;
            var bookId = jdbcTemplate.queryForObject(
                    "insert into book (isbn, title, category_id) values (?, ?, ?) returning id", Long.class,
                    String.format("97800001%05d", number), "Title " + number, categoryId);
            var readerId = jdbcTemplate.queryForObject("""
                    insert into reader (name, document, email, birth_date)
                    values (?, ?, 'r@example.com', date '1990-01-01') returning id
                    """, Long.class, "Reader " + number, "RQC-DOC-" + number);
            if (number % 2 == 0) {
                jdbcTemplate.update("""
                        insert into reservation (book_id, reader_id, reservation_date, status)
                        values (?, ?, ?, 'ACTIVE')
                        """, bookId, readerId, DAY);
            } else {
                var copyId = jdbcTemplate.queryForObject("""
                        insert into copy (book_id, asset_code, status) values (?, ?, 'RESERVED') returning id
                        """, Long.class, bookId, "RQC-" + number);
                jdbcTemplate.update("""
                        insert into reservation (book_id, reader_id, reservation_date, status, copy_id, pickup_deadline)
                        values (?, ?, ?, 'ACTIVE', ?, ?)
                        """, bookId, readerId, DAY, copyId, DAY.plusDays(3));
            }
        }
    }
}
