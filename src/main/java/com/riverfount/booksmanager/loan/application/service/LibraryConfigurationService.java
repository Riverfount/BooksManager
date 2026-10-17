package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.GetLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationCommand;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the library configuration use cases: loads, lets the domain
 * validate and apply the change, and persists. The rules are the domain's.
 */
@Service
@Transactional
class LibraryConfigurationService implements GetLibraryConfigurationUseCase, UpdateLibraryConfigurationUseCase {

    private final LibraryConfigurationRepository repository;

    LibraryConfigurationService(LibraryConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryConfiguration get() {
        return repository.get();
    }

    /**
     * The configuration is loaded locked, so two administrators changing different
     * values at the same time are handled one after the other and neither
     * overwrites the other's change. If the domain refuses a value, nothing is saved.
     */
    @Override
    public LibraryConfiguration update(UpdateLibraryConfigurationCommand command) {
        var configuration = repository.getForUpdate();
        configuration.update(command.penaltyPolicy(), command.dailyFineAmount(), command.fineCap(),
                command.suspensionDaysPerDay(), command.suspensionCapDays(), command.loanPeriodDays(),
                command.loanLimit(), command.renewalLimit(), command.reservationPickupDays());
        return repository.save(configuration);
    }
}
