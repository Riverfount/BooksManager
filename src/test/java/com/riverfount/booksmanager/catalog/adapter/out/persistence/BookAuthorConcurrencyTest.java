package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.application.port.out.BookRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The book and the author under concurrency (RNF17): two transactions changing the same record at the same time do
 * not overwrite each other. The first to commit wins, and the other one gets the optimistic locking failure (the 409
 * of the API) instead of silently replacing what was saved. Runs with no test transaction and separate threads, as
 * the real requests do, because a test transaction would hide the lock.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookAuthorConcurrencyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long authorId;
    private long otherAuthorId;
    private long bookId;

    @BeforeEach
    void createParents() {
        cleanUp();
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Book concurrency test') returning id", Long.class);
        authorId = jdbcTemplate.queryForObject(
                "insert into author (name) values ('Book concurrency author') returning id", Long.class);
        otherAuthorId = jdbcTemplate.queryForObject(
                "insert into author (name) values ('Book concurrency other') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, publisher, publication_year, category_id)
                values ('9780000000330', 'Book concurrency', 'Garnier', 1899, ?) returning id
                """, Long.class, categoryId);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", bookId, authorId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from book_author where author_id in (select id from author where name like 'Book concurrency%')");
        jdbcTemplate.update("delete from book where isbn = '9780000000330'");
        jdbcTemplate.update("delete from author where name like 'Book concurrency%'");
        jdbcTemplate.update("delete from category where name = 'Book concurrency test'");
    }

    @Test
    void twoTransactionsChangingTheSameBookShouldNotOverwriteEachOther() throws Exception {
        var firstHasLoaded = new CountDownLatch(1);
        var secondHasCommitted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(1);
        try {
            var first = pool.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    var book = books.findById(bookId).orElseThrow();
                    firstHasLoaded.countDown();
                    await(secondHasCommitted);
                    book.replaceAuthors(Set.of(otherAuthorId));
                    books.save(book);
                });
                return null;
            });

            firstHasLoaded.await();
            transactions.executeWithoutResult(status -> {
                var book = books.findById(bookId).orElseThrow();
                book.updateDetails("Changed by the second", "Garnier", 1899, categoryId, CLOCK);
                books.save(book);
            });
            secondHasCommitted.countDown();

            assertThatThrownBy(() -> first.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            pool.shutdownNow();
        }

        var stored = storedBook();
        assertThat(stored.getTitle()).as("the committed change stays").isEqualTo("Changed by the second");
        assertThat(stored.getAuthorIds()).as("the stale replacement of the authors was refused")
                .containsExactly(authorId);
    }

    @Test
    void twoTransactionsReplacingTheAuthorsOfTheSameBookShouldNotLoseOne() throws Exception {
        var firstHasLoaded = new CountDownLatch(1);
        var secondHasCommitted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(1);
        try {
            var first = pool.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    var book = books.findById(bookId).orElseThrow();
                    firstHasLoaded.countDown();
                    await(secondHasCommitted);
                    book.replaceAuthors(Set.of(authorId, otherAuthorId));
                    books.save(book);
                });
                return null;
            });

            firstHasLoaded.await();
            transactions.executeWithoutResult(status -> {
                var book = books.findById(bookId).orElseThrow();
                book.replaceAuthors(Set.of(otherAuthorId));
                books.save(book);
            });
            secondHasCommitted.countDown();

            assertThatThrownBy(() -> first.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            pool.shutdownNow();
        }

        assertThat(storedBook().getAuthorIds()).containsExactly(otherAuthorId);
    }

    @Test
    void twoTransactionsChangingTheSameAuthorShouldNotOverwriteEachOther() throws Exception {
        var firstHasLoaded = new CountDownLatch(1);
        var secondHasCommitted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(1);
        try {
            var first = pool.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    var author = authors.findById(authorId).orElseThrow();
                    firstHasLoaded.countDown();
                    await(secondHasCommitted);
                    author.deactivate();
                    authors.save(author);
                });
                return null;
            });

            firstHasLoaded.await();
            transactions.executeWithoutResult(status -> {
                var author = authors.findById(authorId).orElseThrow();
                author.rename("Book concurrency renamed");
                authors.save(author);
            });
            secondHasCommitted.countDown();

            assertThatThrownBy(() -> first.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            pool.shutdownNow();
        }

        var stored = authors.findById(authorId).orElseThrow();
        assertThat(stored.getName()).as("the committed rename stays").isEqualTo("Book concurrency renamed");
        assertThat(stored.isActive()).as("the stale deactivation was refused").isTrue();
    }

    @Test
    void sequentialChangesOfTheSameBookAndAuthorShouldNeverConflict() {
        for (var i = 1; i <= 3; i++) {
            var title = "Book concurrency " + i;
            transactions.executeWithoutResult(status -> {
                var book = books.findById(bookId).orElseThrow();
                book.updateDetails(title, "Garnier", 1899, categoryId, CLOCK);
                books.save(book);
            });
            var name = "Book concurrency author " + i;
            transactions.executeWithoutResult(status -> {
                var author = authors.findById(authorId).orElseThrow();
                author.rename(name);
                authors.save(author);
            });
        }

        assertThat(storedBook().getTitle()).isEqualTo("Book concurrency 3");
        assertThat(authors.findById(authorId).orElseThrow().getName()).isEqualTo("Book concurrency author 3");
    }

    @Test
    void savingTheSameDataAgainShouldNotBumpTheVersion() {
        var before = jdbcTemplate.queryForObject("select version from book where id = ?", Long.class, bookId);

        transactions.executeWithoutResult(status -> books.save(books.findById(bookId).orElseThrow()));

        assertThat(jdbcTemplate.queryForObject("select version from book where id = ?", Long.class, bookId))
                .isEqualTo(before);
    }

    @Test
    void aRegisteredBookAndAuthorShouldStartAtVersionZero() {
        assertThat(jdbcTemplate.queryForObject("select version from book where id = ?", Long.class, bookId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("select version from author where id = ?", Long.class, authorId))
                .isZero();
    }

    /** The book read the way a use case does, inside a transaction (the authors are loaded lazily). */
    private com.riverfount.booksmanager.catalog.domain.Book storedBook() {
        return transactions.execute(status -> books.findById(bookId).orElseThrow());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the other transaction did not commit in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
