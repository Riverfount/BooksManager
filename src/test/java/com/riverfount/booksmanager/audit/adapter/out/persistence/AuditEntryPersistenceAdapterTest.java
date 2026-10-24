package com.riverfount.booksmanager.audit.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.audit.application.port.out.AuditFilter;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the audit persistence adapter against a real PostgreSQL (Testcontainers): the mapping between the domain
 * and the entity, the listing filters and order, and the guarantee that a stored line cannot be rewritten (RF27).
 */
@PersistenceTest
class AuditEntryPersistenceAdapterTest {

    private static final Instant T0 = Instant.parse("2026-10-10T12:00:00Z");
    private static final AuditFilter NO_FILTER = new AuditFilter(null, null, null, null, null, null);

    @Autowired
    private AuditEntryPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static PageQuery newestFirst() {
        return new PageQuery(0, 100, "occurredAt", SortDirection.DESC);
    }

    private AuditEntry add(Instant at, Long actorId, String login, AuditAction action, AuditTarget target,
            Long targetId, String summary) {
        return adapter.save(AuditEntry.record(at, actorId, login, action, target, targetId, summary));
    }

    /** Only the lines of this test: the PostgreSQL container is shared, so the others' lines may be there too. */
    private static AuditFilter byActor(long actorId) {
        return new AuditFilter(actorId, null, null, null, null, null);
    }

    @Test
    void shouldStoreAndReadBackEveryField() {
        var saved = add(T0, 9001L, "maria", AuditAction.FINE_WAIVED, AuditTarget.FINE, 42L, "multa abonada: erro");

        var found = adapter.findAll(byActor(9001L), newestFirst()).items();

        assertThat(saved.id()).isNotNull();
        assertThat(found).containsExactly(saved);
        assertThat(found.getFirst().occurredAt()).isEqualTo(T0);
        assertThat(found.getFirst().summary()).isEqualTo("multa abonada: erro");
    }

    @Test
    void shouldStoreAnEntryWithoutActorTargetAndSummary() {
        var saved = add(T0, null, null, AuditAction.LOGIN_FAILURE, null, null, null);

        var loaded = jdbcTemplate.queryForMap("select * from audit_log where id = ?", saved.id());

        assertThat(loaded.get("actor_id")).isNull();
        assertThat(loaded.get("target_type")).isNull();
        assertThat(loaded.get("summary")).isNull();
    }

    @Test
    void shouldListTheNewestFirst() {
        add(T0, 9002L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "primeiro");
        add(T0.plusSeconds(120), 9002L, "ana", AuditAction.USER_UPDATED, AuditTarget.USER, 1L, "terceiro");
        add(T0.plusSeconds(60), 9002L, "ana", AuditAction.USER_UNBLOCKED, AuditTarget.USER, 1L, "segundo");

        var items = adapter.findAll(byActor(9002L), newestFirst()).items();

        assertThat(items).extracting(AuditEntry::summary).containsExactly("terceiro", "segundo", "primeiro");
    }

    @Test
    void shouldFilterByActorActionTargetAndTargetId() {
        add(T0, 9003L, "ana", AuditAction.FINE_PAID, AuditTarget.FINE, 10L, null);
        add(T0, 9003L, "ana", AuditAction.FINE_WAIVED, AuditTarget.FINE, 11L, null);
        add(T0, 9003L, "ana", AuditAction.READER_BLOCKED, AuditTarget.READER, 10L, null);
        add(T0, 9004L, "bia", AuditAction.FINE_PAID, AuditTarget.FINE, 10L, null);

        assertThat(adapter.findAll(byActor(9003L), newestFirst()).totalItems()).isEqualTo(3);
        assertThat(adapter.findAll(new AuditFilter(9003L, AuditAction.FINE_WAIVED, null, null, null, null),
                newestFirst()).items()).extracting(AuditEntry::targetId).containsExactly(11L);
        assertThat(adapter.findAll(new AuditFilter(9003L, null, AuditTarget.READER, null, null, null),
                newestFirst()).items()).extracting(AuditEntry::action).containsExactly(AuditAction.READER_BLOCKED);
        assertThat(adapter.findAll(new AuditFilter(9003L, null, AuditTarget.FINE, 10L, null, null),
                newestFirst()).items()).extracting(AuditEntry::action).containsExactly(AuditAction.FINE_PAID);
    }

    @Test
    void shouldFilterByPeriodIncludingTheStartAndExcludingTheEnd() {
        add(T0.minusSeconds(1), 9005L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "antes");
        add(T0, 9005L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "no início");
        add(T0.plusSeconds(59), 9005L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "dentro");
        add(T0.plusSeconds(60), 9005L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "no fim");

        var items = adapter.findAll(new AuditFilter(9005L, null, null, null, T0, T0.plusSeconds(60)),
                newestFirst()).items();

        assertThat(items).extracting(AuditEntry::summary).containsExactly("dentro", "no início");
    }

    @Test
    void shouldFilterByAnOpenEndedPeriod() {
        add(T0, 9006L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "cedo");
        add(T0.plusSeconds(100), 9006L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "tarde");

        assertThat(adapter.findAll(new AuditFilter(9006L, null, null, null, T0.plusSeconds(50), null),
                newestFirst()).items()).extracting(AuditEntry::summary).containsExactly("tarde");
        assertThat(adapter.findAll(new AuditFilter(9006L, null, null, null, null, T0.plusSeconds(50)),
                newestFirst()).items()).extracting(AuditEntry::summary).containsExactly("cedo");
    }

    @Test
    void shouldPaginate() {
        for (var i = 0; i < 5; i++) {
            add(T0.plusSeconds(i), 9007L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, "n" + i);
        }

        var page = adapter.findAll(byActor(9007L), new PageQuery(1, 2, "occurredAt", SortDirection.DESC));

        assertThat(page.totalItems()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items()).extracting(AuditEntry::summary).containsExactly("n2", "n1");
    }

    @Test
    void shouldReadWithoutAnyFilter() {
        add(T0, 9008L, "ana", AuditAction.USER_CREATED, AuditTarget.USER, 1L, null);

        assertThat(adapter.findAll(NO_FILTER, newestFirst()).totalItems()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aStoredLineShouldNotBeAbleToBeRewritten() {
        // the refused UPDATE aborts the transaction of the test, so the proof is the refusal itself
        var saved = add(T0, 9009L, "ana", AuditAction.FINE_WAIVED, AuditTarget.FINE, 1L, "original");

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_log set summary = 'adulterado' where id = ?",
                saved.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void theDatabaseShouldRefuseATargetIdWithoutItsType() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into audit_log (occurred_at, action, target_id) values (now(), 'FINE_PAID', 1)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theDatabaseShouldRefuseAnUnknownAction() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into audit_log (occurred_at, action) values (now(), 'DROP_EVERYTHING')"))
                .isInstanceOf(DataAccessException.class);
    }
}
