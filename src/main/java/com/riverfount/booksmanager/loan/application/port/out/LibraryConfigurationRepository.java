package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;

/**
 * Outbound port for the library configuration (RF26, RN16), in the domain's
 * language. There is a single configuration and it always exists, created with
 * the initial values by the database migration, so there is no "find" that can
 * come back empty and no creation.
 */
public interface LibraryConfigurationRepository {

    /**
     * The current configuration, for reading.
     *
     * @throws IllegalStateException if the row is missing, which only happens if the database was tampered with
     */
    LibraryConfiguration get();

    /**
     * The current configuration, for a change: the row is locked until the end of
     * the current transaction. A change saves the whole row, so a stale read could
     * overwrite what another administrator changed at the same time; the second
     * one waits and reads the result of the first.
     *
     * @throws IllegalStateException if the row is missing
     */
    LibraryConfiguration getForUpdate();

    /** Saves the configuration and returns it as stored. */
    LibraryConfiguration save(LibraryConfiguration configuration);
}
