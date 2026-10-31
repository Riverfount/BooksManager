package com.riverfount.booksmanager.audit.domain;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

    private static final Instant NOW = Instant.parse("2026-10-24T15:00:00Z");

    @Test
    void shouldRecordWhoWhenWhatAndOnWhichTarget() {
        var entry = AuditEntry.record(NOW, 7L, "maria", AuditAction.FINE_WAIVED, AuditTarget.FINE, 42L,
                "multa abonada: erro de cálculo");

        assertThat(entry.id()).isNull();
        assertThat(entry.occurredAt()).isEqualTo(NOW);
        assertThat(entry.actorId()).isEqualTo(7L);
        assertThat(entry.actorLogin()).isEqualTo("maria");
        assertThat(entry.action()).isEqualTo(AuditAction.FINE_WAIVED);
        assertThat(entry.target()).isEqualTo(AuditTarget.FINE);
        assertThat(entry.targetId()).isEqualTo(42L);
        assertThat(entry.summary()).isEqualTo("multa abonada: erro de cálculo");
    }

    @Test
    void shouldAcceptAnEntryWithoutActorAndWithoutTarget() {
        var entry = AuditEntry.record(NOW, null, null, AuditAction.LOGIN_FAILURE, null, null, "login desconhecido");

        assertThat(entry.actorId()).isNull();
        assertThat(entry.actorLogin()).isNull();
        assertThat(entry.target()).isNull();
        assertThat(entry.targetId()).isNull();
    }

    @Test
    void shouldAcceptATargetWithoutId() {
        var entry = AuditEntry.record(NOW, 7L, "maria", AuditAction.LIBRARY_CONFIGURATION_UPDATED,
                AuditTarget.LIBRARY_CONFIGURATION, null, null);

        assertThat(entry.target()).isEqualTo(AuditTarget.LIBRARY_CONFIGURATION);
        assertThat(entry.targetId()).isNull();
    }

    @Test
    void shouldRequireTheMomentAndTheAction() {
        assertThatThrownBy(() -> AuditEntry.record(null, 7L, "maria", AuditAction.FINE_PAID, AuditTarget.FINE, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEntry.record(NOW, 7L, "maria", null, AuditTarget.FINE, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotAcceptATargetIdWithoutTheTargetType() {
        assertThatThrownBy(() -> AuditEntry.record(NOW, 7L, "maria", AuditAction.FINE_PAID, null, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alvo");
    }

    @Test
    void shouldNotAcceptALoginWithoutTheActorId() {
        assertThatThrownBy(() -> AuditEntry.record(NOW, null, "maria", AuditAction.FINE_PAID, AuditTarget.FINE, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autor");
    }

    @Test
    void shouldTrimTheSummaryAndTurnABlankOneIntoNull() {
        assertThat(AuditEntry.record(NOW, 7L, "maria", AuditAction.FINE_PAID, AuditTarget.FINE, 1L, "  pago  ")
                .summary()).isEqualTo("pago");
        assertThat(AuditEntry.record(NOW, 7L, "maria", AuditAction.FINE_PAID, AuditTarget.FINE, 1L, "   ")
                .summary()).isNull();
    }

    @Test
    void shouldCutALongSummaryInsteadOfRefusingTheOperation() {
        var entry = AuditEntry.record(NOW, 7L, "maria", AuditAction.FINE_WAIVED, AuditTarget.FINE, 1L,
                "x".repeat(AuditEntry.MAX_SUMMARY_LENGTH + 100));

        assertThat(entry.summary()).hasSize(AuditEntry.MAX_SUMMARY_LENGTH).endsWith("…");
    }

    @Test
    void shouldKeepASummaryOfExactlyTheLimit() {
        var exact = "x".repeat(AuditEntry.MAX_SUMMARY_LENGTH);

        assertThat(AuditEntry.record(NOW, 7L, "maria", AuditAction.FINE_WAIVED, AuditTarget.FINE, 1L, exact)
                .summary()).isEqualTo(exact);
    }

    @Test
    void shouldReconstituteAStoredEntryWithItsId() {
        var entry = AuditEntry.reconstitute(5L, NOW, 7L, "maria", AuditAction.FINE_PAID, AuditTarget.FINE, 1L, "pago");

        assertThat(entry.id()).isEqualTo(5L);
    }

    @Test
    void everyActionShouldHaveItsOwnName() {
        assertThat(AuditAction.values()).extracting(Enum::name).doesNotHaveDuplicates().hasSize(31);
    }
}
