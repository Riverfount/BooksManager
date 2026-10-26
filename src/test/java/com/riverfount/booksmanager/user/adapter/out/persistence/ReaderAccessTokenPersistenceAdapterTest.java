package com.riverfount.booksmanager.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.user.domain.Reader;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken.Purpose;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Tests the reader access token adapter against a real PostgreSQL (Testcontainers): the mapping, the lookup by hash
 * and the invalidation of the pending tokens of a reader (Phase 6).
 */
@PersistenceTest
class ReaderAccessTokenPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Duration VALIDITY = Duration.ofHours(72);

    @Autowired
    private ReaderAccessTokenPersistenceAdapter adapter;

    @Autowired
    private ReaderPersistenceAdapter readers;

    private Long saveReader(String document) {
        return readers.save(Reader.create("Ana Souza", document, "ana@example.com", null,
                LocalDate.of(1990, 5, 10), null, LocalDate.of(2026, 10, 26))).getId();
    }

    private static String hash(String token) {
        return ReaderAccessToken.hashOf(token);
    }

    @Test
    void shouldSaveAndFindATokenByItsHashWithTheSameState() {
        var readerId = saveReader("TK-1");

        var saved = adapter.save(ReaderAccessToken.issue(Purpose.INVITATION, readerId, hash("t1"), NOW, VALIDITY));

        var found = adapter.findByTokenHashForUpdate(hash("t1")).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getPurpose()).isEqualTo(Purpose.INVITATION);
        assertThat(found.getReaderId()).isEqualTo(readerId);
        assertThat(found.getTokenHash()).isEqualTo(hash("t1"));
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getExpiresAt()).isEqualTo(NOW.plus(VALIDITY));
        assertThat(found.getUsedAt()).isNull();
    }

    @Test
    void shouldNotFindAnUnknownOrNullHash() {
        assertThat(adapter.findByTokenHashForUpdate(hash("nobody"))).isEmpty();
        assertThat(adapter.findByTokenHashForUpdate(null)).isEmpty();
    }

    @Test
    void shouldPersistTheUseOfAToken() {
        var saved = adapter.save(ReaderAccessToken.issue(Purpose.PASSWORD_RESET, saveReader("TK-2"), hash("t2"), NOW,
                VALIDITY));
        saved.use(NOW.plusSeconds(30));

        adapter.save(saved);

        assertThat(adapter.findByTokenHashForUpdate(hash("t2")).orElseThrow().getUsedAt())
                .isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void shouldRefuseARepeatedHash() {
        var readerId = saveReader("TK-3");
        adapter.save(ReaderAccessToken.issue(Purpose.INVITATION, readerId, hash("same"), NOW, VALIDITY));

        assertThatThrownBy(() -> adapter.save(ReaderAccessToken.issue(Purpose.INVITATION, readerId, hash("same"),
                NOW, VALIDITY))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldInvalidateOnlyThePendingTokensOfTheReaderAndThePurpose() {
        var ana = saveReader("TK-4");
        var bia = saveReader("TK-5");
        adapter.save(ReaderAccessToken.issue(Purpose.INVITATION, ana, hash("a1"), NOW, VALIDITY));
        adapter.save(ReaderAccessToken.issue(Purpose.INVITATION, ana, hash("a2"), NOW, VALIDITY));
        adapter.save(ReaderAccessToken.issue(Purpose.PASSWORD_RESET, ana, hash("a3"), NOW, VALIDITY));
        adapter.save(ReaderAccessToken.issue(Purpose.INVITATION, bia, hash("b1"), NOW, VALIDITY));
        var alreadyUsed = adapter.save(ReaderAccessToken.issue(Purpose.INVITATION, ana, hash("a4"), NOW, VALIDITY));
        alreadyUsed.use(NOW.plusSeconds(1));
        adapter.save(alreadyUsed);

        var invalidated = adapter.invalidatePending(ana, Purpose.INVITATION, NOW.plusSeconds(60));

        assertThat(invalidated).isEqualTo(2);
        assertThat(adapter.findByTokenHashForUpdate(hash("a1")).orElseThrow().isUsed()).isTrue();
        assertThat(adapter.findByTokenHashForUpdate(hash("a2")).orElseThrow().isUsed()).isTrue();
        assertThat(adapter.findByTokenHashForUpdate(hash("a3")).orElseThrow().isUsed()).isFalse();
        assertThat(adapter.findByTokenHashForUpdate(hash("b1")).orElseThrow().isUsed()).isFalse();
        assertThat(adapter.findByTokenHashForUpdate(hash("a4")).orElseThrow().getUsedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
