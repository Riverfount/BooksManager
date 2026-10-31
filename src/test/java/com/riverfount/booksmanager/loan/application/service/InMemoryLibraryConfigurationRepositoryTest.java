package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import org.junit.jupiter.api.Test;

/** The fake must behave like the adapter, or the service tests that use it would prove nothing. */
class InMemoryLibraryConfigurationRepositoryTest {

    private final InMemoryLibraryConfigurationRepository repository = new InMemoryLibraryConfigurationRepository();

    @Test
    void shouldStartWithTheInitialValues() {
        var configuration = repository.get();

        assertThat(configuration.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.FINE);
        assertThat(configuration.getLoanPeriodDays()).isEqualTo(14);
        assertThat(configuration.getFineCap()).isEqualByComparingTo("10.00");
    }

    @Test
    void aChangeOnlyTakesEffectWhenItIsSaved() {
        var configuration = repository.get();
        configuration.update(PenaltyPolicy.NONE, null, null, null, null, null, null, null, null, null, null);

        assertThat(repository.get().getPenaltyPolicy()).isEqualTo(PenaltyPolicy.FINE);

        repository.save(configuration);

        assertThat(repository.get().getPenaltyPolicy()).isEqualTo(PenaltyPolicy.NONE);
        assertThat(repository.getForUpdate().getPenaltyPolicy()).isEqualTo(PenaltyPolicy.NONE);
        assertThat(repository.saves()).isEqualTo(1);
    }
}
