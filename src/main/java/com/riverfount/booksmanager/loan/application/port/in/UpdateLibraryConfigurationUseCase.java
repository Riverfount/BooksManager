package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;

/**
 * Changes the library configuration (RF26, HU13, RN16). The change applies only to
 * the delays that come after it: what was already returned is not recalculated.
 */
public interface UpdateLibraryConfigurationUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException when a value breaks a rule;
     *         nothing is changed
     */
    LibraryConfiguration update(UpdateLibraryConfigurationCommand command);
}
