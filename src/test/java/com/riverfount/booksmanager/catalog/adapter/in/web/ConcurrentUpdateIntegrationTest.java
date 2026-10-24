package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Many requests changing the same book (and the same author) at the same moment (RNF17), through the real path:
 * security chain, transaction, optimistic lock and PostgreSQL. Which ones collide depends on the timing, so the test
 * does not say how many conflicts there are; it says what must never happen: an answer that is neither 200 nor 409
 * (a 500, for example), and a record left in a state that no request asked for. The data is committed, so it is
 * removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ConcurrentUpdateIntegrationTest {

    private static final int REQUESTS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long bookId;
    private long authorId;
    private long otherAuthorId;

    @BeforeEach
    void createTheBookAndTheAuthors() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Concurrent update test') returning id", Long.class);
        authorId = jdbcTemplate.queryForObject(
                "insert into author (name) values ('Concurrent update author') returning id", Long.class);
        otherAuthorId = jdbcTemplate.queryForObject(
                "insert into author (name) values ('Concurrent update other') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, publisher, publication_year, category_id)
                values ('9780000000347', 'Concurrent update', 'Garnier', 1899, ?) returning id
                """, Long.class, categoryId);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", bookId, authorId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from book_author where author_id in (select id from author where name like 'Concurrent update%')");
        jdbcTemplate.update("delete from book where isbn = '9780000000347'");
        jdbcTemplate.update("delete from author where name like 'Concurrent update%'");
        jdbcTemplate.update("delete from category where name = 'Concurrent update test'");
    }

    private List<Integer> patchAtTheSameTime(String route, long id, java.util.function.IntFunction<String> body)
            throws Exception {
        var token = "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR");
        var startTogether = new CyclicBarrier(REQUESTS);
        var pool = Executors.newFixedThreadPool(REQUESTS);
        try {
            var calls = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (var i = 0; i < REQUESTS; i++) {
                var content = body.apply(i);
                calls.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(patch("/api/v1/" + route + "/" + id)
                                    .header("Authorization", token)
                                    .header("X-Requested-With", "XMLHttpRequest")
                                    .contentType(MediaType.APPLICATION_JSON).content(content))
                            .andReturn().getResponse().getStatus();
                }));
            }
            var statuses = new ArrayList<Integer>();
            for (var call : calls) {
                statuses.add(call.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void simultaneousReplacementsOfTheAuthorsOfABookShouldAnswerOnly200Or409() throws Exception {
        var statuses = patchAtTheSameTime("books", bookId, i ->
                "{\"authorIds\":[" + (i % 2 == 0 ? authorId : otherAuthorId) + "]}");

        assertThat(statuses).allMatch(status -> status == 200 || status == 409);
        assertThat(statuses).contains(200);
        // the book ends with the authors of one request, never a mix or none
        var authors = jdbcTemplate.queryForList("select author_id from book_author where book_id = ?", Long.class,
                bookId);
        assertThat(authors).hasSize(1).containsAnyOf(authorId, otherAuthorId);
    }

    @Test
    void simultaneousRenamesOfAnAuthorShouldAnswerOnly200Or409() throws Exception {
        var statuses = patchAtTheSameTime("authors", authorId, i -> "{\"name\":\"Concurrent update name " + i + "\"}");

        assertThat(statuses).allMatch(status -> status == 200 || status == 409);
        assertThat(statuses).contains(200);
        assertThat(jdbcTemplate.queryForObject("select name from author where id = ?", String.class, authorId))
                .startsWith("Concurrent update name ");
    }
}
