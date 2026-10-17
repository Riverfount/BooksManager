package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import java.math.BigDecimal;

/**
 * In-memory implementation of LibraryConfigurationRepository, used only in the
 * application services' unit tests (no Spring, no database). Starts with the
 * initial values the migration creates, and like the JPA adapter it never hands
 * out the stored instance, so a change only takes effect when it is saved. Does
 * not need to be thread-safe: it is a test fake, used from a single thread.
 */
class InMemoryLibraryConfigurationRepository implements LibraryConfigurationRepository {

    private LibraryConfiguration stored = LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE,
            new BigDecimal("1.00"), new BigDecimal("10.00"), 1, 365, 14, 3, 2, 3);
    private int saves;

    private static LibraryConfiguration copyOf(LibraryConfiguration c) {
        return LibraryConfiguration.reconstitute(c.getId(), c.getPenaltyPolicy(), c.getDailyFineAmount(),
                c.getFineCap(), c.getSuspensionDaysPerDay(), c.getSuspensionCapDays(), c.getLoanPeriodDays(),
                c.getLoanLimit(), c.getRenewalLimit(), c.getReservationPickupDays());
    }

    @Override
    public LibraryConfiguration get() {
        return copyOf(stored);
    }

    @Override
    public LibraryConfiguration getForUpdate() {
        return copyOf(stored);
    }

    @Override
    public LibraryConfiguration save(LibraryConfiguration configuration) {
        saves++;
        stored = copyOf(configuration);
        return copyOf(stored);
    }

    int saves() {
        return saves;
    }
}
