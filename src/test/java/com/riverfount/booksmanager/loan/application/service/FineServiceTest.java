package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.loan.application.port.in.WaiveFineCommand;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Paying and waiving a fine (RF21, HU10): the service only orchestrates; the rules (a fine
 * is resolved once, a waiver needs a justification) are the domain's.
 */
class FineServiceTest {

    private InMemoryFineRepository fineRepository;
    private FineService service;

    @BeforeEach
    void setUp() {
        fineRepository = new InMemoryFineRepository();
        service = new FineService(fineRepository);
    }

    private Long pendingFine() {
        return fineRepository.save(Fine.create(1L, 20L, new BigDecimal("6.00"))).getId();
    }

    private Fine stored(Long id) {
        return fineRepository.findById(id).orElseThrow();
    }

    // paying

    @Test
    void shouldPayAPendingFine() {
        var id = pendingFine();

        var fine = service.pay(id);

        assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(stored(id).getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(stored(id).isPending()).isFalse();
    }

    @Test
    void shouldNotPayAFineThatWasAlreadyPaid() {
        var id = pendingFine();
        service.pay(id);

        assertThatThrownBy(() -> service.pay(id)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já foi paga");
    }

    @Test
    void shouldNotPayAFineThatWasWaived() {
        var id = pendingFine();
        service.waive(new WaiveFineCommand(id, "Hospital stay"));

        assertThatThrownBy(() -> service.pay(id)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já foi abonada");
        assertThat(stored(id).getStatus()).isEqualTo(FineStatus.WAIVED);
    }

    @Test
    void shouldFailWithNotFoundWhenPayingAFineThatDoesNotExist() {
        assertThatThrownBy(() -> service.pay(999L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // waiving

    @Test
    void shouldWaiveAPendingFineKeepingTheJustification() {
        var id = pendingFine();

        var fine = service.waive(new WaiveFineCommand(id, "  Hospital stay  "));

        assertThat(fine.getStatus()).isEqualTo(FineStatus.WAIVED);
        assertThat(fine.getWaiverReason()).isEqualTo("Hospital stay");
        assertThat(stored(id).getStatus()).isEqualTo(FineStatus.WAIVED);
        assertThat(stored(id).getWaiverReason()).isEqualTo("Hospital stay");
    }

    @Test
    void shouldNotWaiveWithoutAJustificationAndKeepTheFinePending() {
        var id = pendingFine();

        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(id, "   ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justificativa");
        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(id, null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(stored(id).isPending()).isTrue();
    }

    @Test
    void shouldNotWaiveWithAJustificationThatIsTooLong() {
        var id = pendingFine();
        var tooLong = "x".repeat(Fine.MAX_WAIVER_REASON_LENGTH + 1);

        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(id, tooLong)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(stored(id).isPending()).isTrue();
    }

    @Test
    void shouldNotWaiveAFineThatWasAlreadyPaidOrWaivedAndKeepTheFirstOutcome() {
        var paid = pendingFine();
        service.pay(paid);
        var waived = fineRepository.save(Fine.create(2L, 20L, new BigDecimal("3.00"))).getId();
        service.waive(new WaiveFineCommand(waived, "First reason"));

        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(paid, "Reason")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(waived, "Other reason")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(stored(paid).getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(stored(waived).getWaiverReason()).isEqualTo("First reason");
    }

    @Test
    void shouldFailWithNotFoundWhenWaivingAFineThatDoesNotExist() {
        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(999L, "Reason")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
