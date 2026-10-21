package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.in.ListReadersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListReadersUseCase;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the "no query per reader" criterion of the reader listing (RF07,
 * RNF14): the whole use case, with the real adapter and PostgreSQL, runs a
 * fixed number of queries per page (the page and its total count), however
 * many readers the page has.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ReaderSearchQueryCountTest {

    private static final int QUERIES_PER_PAGE = 2;

    @Autowired
    private ListReadersUseCase listReadersUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private int readersCreated;

    @Test
    void listingShouldRunAFixedNumberOfQueriesWhateverThePageSize() {
        createReaders(4);
        var smallPage = queriesFor(new ListReadersQuery(null, null, true, null, PageQuery.of(0, 2, "name")));

        createReaders(56);
        var bigPage = queriesFor(new ListReadersQuery(null, null, true, null, PageQuery.of(0, 50, "name")));

        assertThat(bigPage).isEqualTo(smallPage).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    @Test
    void searchWithAllTheFiltersShouldRunAFixedNumberOfQueriesWhateverThePageSize() {
        createReaders(4);
        var smallPage = queriesFor(new ListReadersQuery("leitor", null, true, false, PageQuery.of(0, 2, "name")));

        createReaders(56);
        var bigPage = queriesFor(new ListReadersQuery("leitor", null, true, false, PageQuery.of(0, 50, "name")));

        assertThat(bigPage).isEqualTo(smallPage).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    private long queriesFor(ListReadersQuery query) {
        // the persistence context must be empty, or cached entities would hide queries
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = listReadersUseCase.list(query);

        assertThat(page.items()).isNotEmpty();
        return statistics.getPrepareStatementCount();
    }

    private void createReaders(int quantity) {
        for (var i = 0; i < quantity; i++) {
            var number = readersCreated++;
            jdbcTemplate.update("insert into reader (name, document, email, birth_date) values (?, ?, ?, date '1990-01-01')",
                    "Leitor " + number, "DOC" + number, "leitor" + number + "@example.com");
        }
    }
}
