package com.riverfount.booksmanager.loan.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts the rows a reservation needs to exist (books, copies and readers), straight into the database. */
final class ReservationTestData {

    private final JdbcTemplate jdbcTemplate;
    private final long categoryId;
    private int sequence;

    ReservationTestData(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Reservation persistence test') returning id", Long.class);
    }

    long newBook() {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, 'T', ?) returning id", Long.class,
                String.format("97800000%05d", ++sequence), categoryId);
    }

    long newCopy(long bookId) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                Long.class, bookId, "RP-" + ++sequence);
    }

    long newReader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values (?, ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, "Reader " + ++sequence, "RP-DOC-" + sequence);
    }
}
