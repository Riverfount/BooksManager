package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.Optional;

/** Public port for the loan module: can this copy be borrowed now? */
public interface FindCopyAvailabilityUseCase {

    /** The availability of the copy, empty when the copy does not exist. */
    Optional<CopyAvailability> findAvailability(Long copyId);
}
