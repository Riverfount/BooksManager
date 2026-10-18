package com.riverfount.booksmanager.loan.adapter.out.persistence;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts the rows a loan needs to exist (a copy and a reader), straight into the database. */
final class LoanTestData {

    private final JdbcTemplate jdbcTemplate;
    private final long bookId;
    private int sequence;

    LoanTestData(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Loan persistence test') returning id", Long.class);
        this.bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000019', 'T', ?) returning id",
                Long.class, categoryId);
    }

    long newCopy() {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                Long.class, bookId, "LP-" + ++sequence);
    }

    long newReader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values (?, ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, "Reader " + ++sequence, "LP-DOC-" + sequence);
    }

    long newReturnedLoan(long copyId, long readerId, LocalDate pickup) {
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, ?, ?, ?, 'GOOD') returning id
                """, Long.class, copyId, readerId, pickup, pickup.plusDays(14), pickup.plusDays(20));
    }
}
