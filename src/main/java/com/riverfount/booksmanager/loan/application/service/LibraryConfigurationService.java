package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.loan.application.port.in.GetLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationCommand;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the library configuration use cases: loads, lets the domain
 * validate and apply the change, and persists. The rules are the domain's. A change is reported to the audit trail
 * (RF27) with the values that were set (they are the library's own settings, never a secret).
 */
@Service
@Transactional
class LibraryConfigurationService implements GetLibraryConfigurationUseCase, UpdateLibraryConfigurationUseCase {

    private final LibraryConfigurationRepository repository;
    private final RecordAuditEventUseCase audit;

    LibraryConfigurationService(LibraryConfigurationRepository repository, RecordAuditEventUseCase audit) {
        this.repository = repository;
        this.audit = audit;
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
                command.loanLimit(), command.renewalLimit(), command.reservationPickupDays(),
                command.dueSoonNoticeDays(), command.assetCodePrefix());
        var saved = repository.save(configuration);
        audit.record(new AuditEvent(AuditAction.LIBRARY_CONFIGURATION_UPDATED, AuditTarget.LIBRARY_CONFIGURATION, null,
                describe(command)));
        return saved;
    }

    /** The values the command set, as {@code name=value}; a field left out (kept as it was) is not listed. */
    private static String describe(UpdateLibraryConfigurationCommand command) {
        var changes = new ArrayList<String>();
        add(changes, "penaltyPolicy", command.penaltyPolicy());
        add(changes, "dailyFineAmount", command.dailyFineAmount() == null ? null : command.dailyFineAmount().toPlainString());
        add(changes, "fineCap", command.fineCap() == null ? null : command.fineCap().toPlainString());
        add(changes, "suspensionDaysPerDay", command.suspensionDaysPerDay());
        add(changes, "suspensionCapDays", command.suspensionCapDays());
        add(changes, "loanPeriodDays", command.loanPeriodDays());
        add(changes, "loanLimit", command.loanLimit());
        add(changes, "renewalLimit", command.renewalLimit());
        add(changes, "reservationPickupDays", command.reservationPickupDays());
        add(changes, "dueSoonNoticeDays", command.dueSoonNoticeDays());
        add(changes, "assetCodePrefix", command.assetCodePrefix());
        return changes.isEmpty() ? null : String.join(", ", changes);
    }

    private static void add(ArrayList<String> changes, String name, Object value) {
        if (value != null) {
            changes.add(name + "=" + Objects.toString(value));
        }
    }
}
