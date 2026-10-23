package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The fine a late return generates (RF21, HU10): paid or waived once, never twice. */
class FineTest {

    private static Fine fine() {
        return Fine.create(10L, 20L, new BigDecimal("3.00"));
    }

    @Test
    void shouldHaveExactlyTheThreeStatuses() {
        assertThat(FineStatus.values()).containsExactly(FineStatus.PENDING, FineStatus.PAID, FineStatus.WAIVED);
    }

    // creation

    @Test
    void shouldCreateAPendingFineForTheLoanAndTheReader() {
        var fine = fine();

        assertThat(fine.getId()).isNull();
        assertThat(fine.getLoanId()).isEqualTo(10L);
        assertThat(fine.getReaderId()).isEqualTo(20L);
        assertThat(fine.getAmount()).isEqualByComparingTo("3.00");
        assertThat(fine.getStatus()).isEqualTo(FineStatus.PENDING);
        assertThat(fine.isPending()).isTrue();
        assertThat(fine.getWaiverReason()).isNull();
    }

    @Test
    void shouldKeepTheAmountWithTwoDecimalPlaces() {
        assertThat(Fine.create(1L, 2L, new BigDecimal("1.5")).getAmount().toPlainString()).isEqualTo("1.50");
        assertThat(Fine.create(1L, 2L, new BigDecimal("10")).getAmount().toPlainString()).isEqualTo("10.00");
    }

    @Test
    void theAmountMustBePositive() {
        for (var amount : new String[] {"0", "0.00", "-0.01", "-5"}) {
            assertThatThrownBy(() -> Fine.create(10L, 20L, new BigDecimal(amount)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("valor da multa");
        }
        assertThatThrownBy(() -> Fine.create(10L, 20L, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("valor da multa");
    }

    @Test
    void theAmountHasAtMostTwoDecimalPlacesAndFitsTheColumn() {
        assertThatThrownBy(() -> Fine.create(10L, 20L, new BigDecimal("1.001")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("duas casas");
        assertThatThrownBy(() -> Fine.create(10L, 20L, new BigDecimal("100000000.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("valor da multa");
        assertThat(Fine.create(10L, 20L, new BigDecimal("99999999.99")).getAmount()).isEqualByComparingTo("99999999.99");
    }

    @Test
    void shouldRequireTheLoanAndTheReader() {
        assertThatThrownBy(() -> Fine.create(null, 20L, new BigDecimal("3.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("empréstimo");
        assertThatThrownBy(() -> Fine.create(10L, null, new BigDecimal("3.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("leitor");
    }

    @Test
    void shouldBeBuiltFromThePenaltyOfThePolicy() {
        var penalty = PenaltyPolicy.FINE.calculate(3, LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE,
                new BigDecimal("1.00"), new BigDecimal("10.00"), 1, 365, 14, 3, 2, 3, 2));

        var fine = Fine.create(10L, 20L, penalty.fineAmount());

        assertThat(fine.getAmount()).isEqualByComparingTo("3.00");
    }

    // paying (RF21)

    @Test
    void shouldPayAPendingFine() {
        var fine = fine();

        fine.pay();

        assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(fine.isPending()).isFalse();
        assertThat(fine.getWaiverReason()).isNull();
        assertThat(fine.getAmount()).isEqualByComparingTo("3.00");
    }

    @Test
    void shouldRefuseToPayAFineAlreadyPaidKeepingItPaid() {
        var fine = fine();
        fine.pay();

        assertThatThrownBy(fine::pay)
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já foi paga");
        assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
    }

    @Test
    void shouldRefuseToPayAFineAlreadyWaived() {
        var fine = fine();
        fine.waive("Livro devolvido por engano da biblioteca");

        assertThatThrownBy(fine::pay)
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já foi abonada");
        assertThat(fine.getStatus()).isEqualTo(FineStatus.WAIVED);
    }

    // waiving (RF21): the reason is required and recorded

    @Test
    void shouldWaiveAPendingFineRecordingTheReason() {
        var fine = fine();

        fine.waive("  Atraso causado por greve do transporte  ");

        assertThat(fine.getStatus()).isEqualTo(FineStatus.WAIVED);
        assertThat(fine.isPending()).isFalse();
        assertThat(fine.getWaiverReason()).isEqualTo("Atraso causado por greve do transporte");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void shouldRefuseToWaiveWithoutAReasonKeepingItPending(String reason) {
        var fine = fine();

        assertThatThrownBy(() -> fine.waive(reason))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justificativa");
        assertThat(fine.getStatus()).isEqualTo(FineStatus.PENDING);
        assertThat(fine.getWaiverReason()).isNull();
    }

    @Test
    void theReasonHasALimitOfSize() {
        var fine = fine();

        assertThatThrownBy(() -> fine.waive("x".repeat(Fine.MAX_WAIVER_REASON_LENGTH + 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justificativa");
        assertThat(fine.isPending()).isTrue();

        fine.waive("x".repeat(Fine.MAX_WAIVER_REASON_LENGTH));
        assertThat(fine.getStatus()).isEqualTo(FineStatus.WAIVED);
    }

    @Test
    void shouldRefuseToWaiveAFineAlreadyWaivedKeepingTheFirstReason() {
        var fine = fine();
        fine.waive("primeira justificativa");

        assertThatThrownBy(() -> fine.waive("outra justificativa"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já foi abonada");
        assertThat(fine.getWaiverReason()).isEqualTo("primeira justificativa");
    }

    @Test
    void shouldRefuseToWaiveAFineAlreadyPaid() {
        var fine = fine();
        fine.pay();

        assertThatThrownBy(() -> fine.waive("tarde demais"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já foi paga");
        assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(fine.getWaiverReason()).isNull();
    }

    @Test
    void aResolvedFineIsReportedAsResolvedBeforeAnyReasonCheck() {
        var fine = fine();
        fine.pay();

        // even with no reason, the answer is that it was already paid
        assertThatThrownBy(() -> fine.waive(null)).hasMessageContaining("já foi paga");
    }

    // reconstitution

    @Test
    void shouldReconstituteEachStatusWithItsState() {
        var pending = Fine.reconstitute(1L, 10L, 20L, new BigDecimal("3.00"), FineStatus.PENDING, null);
        var paid = Fine.reconstitute(2L, 10L, 20L, new BigDecimal("3.00"), FineStatus.PAID, null);
        var waived = Fine.reconstitute(3L, 10L, 20L, new BigDecimal("3.00"), FineStatus.WAIVED, "motivo");

        assertThat(pending.isPending()).isTrue();
        assertThat(paid.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(waived.getWaiverReason()).isEqualTo("motivo");
        assertThat(waived.getId()).isEqualTo(3L);
    }

    @Test
    void shouldRefuseAnInconsistentPersistedState() {
        var amount = new BigDecimal("3.00");

        assertThatThrownBy(() -> Fine.reconstitute(1L, 10L, 20L, amount, FineStatus.WAIVED, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Fine.reconstitute(1L, 10L, 20L, amount, FineStatus.WAIVED, "  "))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Fine.reconstitute(1L, 10L, 20L, amount, FineStatus.PAID, "motivo"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Fine.reconstitute(1L, 10L, 20L, amount, FineStatus.PENDING, "motivo"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Fine.reconstitute(1L, 10L, 20L, amount, null, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Fine.reconstitute(1L, 10L, 20L, BigDecimal.ZERO, FineStatus.PENDING, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
