package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationCommand;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The use cases of the library configuration (RF26, HU13, RN16) over the in-memory port. */
class LibraryConfigurationServiceTest {

    private InMemoryLibraryConfigurationRepository repository;
    private LibraryConfigurationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLibraryConfigurationRepository();
        service = new LibraryConfigurationService(repository);
    }

    private static UpdateLibraryConfigurationCommand command(PenaltyPolicy policy, String dailyFine, String fineCap,
            Integer daysPerDay, Integer suspensionCap, Integer period, Integer loanLimit, Integer renewalLimit,
            Integer pickupDays) {
        return new UpdateLibraryConfigurationCommand(policy, dailyFine == null ? null : new BigDecimal(dailyFine),
                fineCap == null ? null : new BigDecimal(fineCap), daysPerDay, suspensionCap, period, loanLimit,
                renewalLimit, pickupDays);
    }

    // reading

    @Test
    void shouldGetTheCurrentConfiguration() {
        var configuration = service.get();

        assertThat(configuration.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.FINE);
        assertThat(configuration.getLoanPeriodDays()).isEqualTo(14);
        assertThat(repository.saves()).isZero();
        assertThat(repository.lockedReads()).isZero();
    }

    // updating

    @Test
    void shouldChangeOnlyTheValuesInformedAndKeepTheRest() {
        var updated = service.update(command(PenaltyPolicy.BOTH, null, null, null, null, 21, null, null, null));

        assertThat(updated.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.BOTH);
        assertThat(updated.getLoanPeriodDays()).isEqualTo(21);
        assertThat(updated.getLoanLimit()).isEqualTo(3);
        assertThat(updated.getFineCap()).isEqualByComparingTo("10.00");
        // what was returned is what was stored
        var stored = repository.get();
        assertThat(stored.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.BOTH);
        assertThat(stored.getLoanPeriodDays()).isEqualTo(21);
    }

    @Test
    void shouldChangeEveryValueWhenAllAreInformed() {
        service.update(command(PenaltyPolicy.SUSPENSION, "2.50", "30.00", 2, 90, 7, 5, 0, 1));

        var stored = repository.get();
        assertThat(stored.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.SUSPENSION);
        assertThat(stored.getDailyFineAmount()).isEqualByComparingTo("2.50");
        assertThat(stored.getFineCap()).isEqualByComparingTo("30.00");
        assertThat(stored.getSuspensionDaysPerDay()).isEqualTo(2);
        assertThat(stored.getSuspensionCapDays()).isEqualTo(90);
        assertThat(stored.getLoanPeriodDays()).isEqualTo(7);
        assertThat(stored.getLoanLimit()).isEqualTo(5);
        assertThat(stored.getRenewalLimit()).isZero();
        assertThat(stored.getReservationPickupDays()).isEqualTo(1);
    }

    @Test
    void shouldReadForUpdateSoTwoAdministratorsDoNotOverwriteEachOther() {
        service.update(command(null, null, null, null, null, 21, null, null, null));

        assertThat(repository.lockedReads()).isEqualTo(1);
    }

    @Test
    void shouldRefuseAnInvalidValueSavingNothingAndKeepingTheConfiguration() {
        assertThatThrownBy(() -> service.update(command(PenaltyPolicy.NONE, null, null, null, null, 0, null, null,
                null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prazo de empréstimo");

        assertThat(repository.saves()).isZero();
        assertThat(repository.get().getPenaltyPolicy()).as("the policy of the same command is not applied alone")
                .isEqualTo(PenaltyPolicy.FINE);
        assertThat(repository.get().getLoanPeriodDays()).isEqualTo(14);
    }

    @Test
    void shouldCheckTheValuesKeptNotOnlyTheOnesInformed() {
        // the cap stays at 10.00, so a daily fine of 20.00 alone would exceed it
        assertThatThrownBy(() -> service.update(command(null, "20.00", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("teto da multa");
        assertThat(repository.saves()).isZero();
    }

    @Test
    void shouldAcceptAPolicyChangeWithoutTouchingTheNumbers() {
        service.update(command(PenaltyPolicy.NONE, null, null, null, null, null, null, null, null));

        var stored = repository.get();
        assertThat(stored.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.NONE);
        assertThat(stored.getDailyFineAmount()).isEqualByComparingTo("1.00");
        assertThat(stored.getSuspensionCapDays()).isEqualTo(365);
    }
}
