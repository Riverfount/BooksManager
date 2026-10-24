package com.riverfount.booksmanager.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogQuery;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogUseCase;
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
 * Guards the "fixed number of queries" criterion of the audit log listing (RF27, RNF14): a page costs the same two
 * queries (the page and the total) whatever its size and however many entries the trail has, so there is never a
 * query per entry.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AuditLogQueryCountTest {

    private static final int QUERIES = 2;

    @Autowired
    private ListAuditLogUseCase auditLog;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aPageShouldRunAFixedNumberOfQueriesWhateverItsSizeAndTheSizeOfTheTrail() {
        addEntries(6);
        var small = queriesFor(ListAuditLogQuery.of(null, null, null, null, null, null, 0, 2));

        addEntries(100);
        var big = queriesFor(ListAuditLogQuery.of(null, null, null, null, null, null, 0, 100));

        assertThat(big).isEqualTo(small).isLessThanOrEqualTo(QUERIES);
    }

    private void addEntries(int count) {
        jdbcTemplate.update("""
                insert into audit_log (occurred_at, actor_id, actor_login, action, target_type, target_id, summary)
                select now() - (g || ' seconds')::interval, 1, 'maria', 'FINE_WAIVED', 'FINE', g, 'abono: motivo'
                from generate_series(1, ?) g
                """, count);
    }

    private long queriesFor(ListAuditLogQuery query) {
        // the persistence context must be empty, or cached entities would hide queries
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = auditLog.list(query);

        assertThat(page.items()).isNotEmpty();
        return statistics.getPrepareStatementCount();
    }
}
