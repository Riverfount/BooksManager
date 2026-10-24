package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.loan.application.port.in.EndSuspensionCommand;
import com.riverfount.booksmanager.loan.application.port.in.EndSuspensionUseCase;
import com.riverfount.booksmanager.loan.application.port.in.SuspensionDetails;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends a suspension before its calculated end (RN15, RF20). It only orchestrates: loads the
 * suspension, lets the domain decide ({@code Suspension.endEarly}: the justification is
 * required, and ending one that is already over changes nothing) and saves only when something
 * changed. Once ended, the suspension is no longer in force, so the loan eligibility stops
 * seeing it (RN05) without anything else being touched. Ending one is reported to the audit trail (RF27) with the
 * justification, only when it actually ended something.
 */
@Service
@Transactional
class EndSuspensionService implements EndSuspensionUseCase {

    private final SuspensionRepository suspensionRepository;
    private final Clock clock;
    private final RecordAuditEventUseCase audit;

    EndSuspensionService(SuspensionRepository suspensionRepository, Clock clock, RecordAuditEventUseCase audit) {
        this.suspensionRepository = suspensionRepository;
        this.clock = clock;
        this.audit = audit;
    }

    @Override
    public SuspensionDetails end(EndSuspensionCommand command) {
        var today = LocalDate.now(clock);
        var suspension = suspensionRepository.findById(command.suspensionId()).orElseThrow(
                () -> new ResourceNotFoundException("suspensão não encontrada: " + command.suspensionId()));

        var ended = suspension.endEarly(today, command.reason());
        var saved = ended ? suspensionRepository.save(suspension) : suspension;
        if (ended) {
            audit.record(new AuditEvent(AuditAction.SUSPENSION_ENDED, AuditTarget.SUSPENSION,
                    command.suspensionId(), "encerrada antes do prazo: " + command.reason().strip()));
        }
        return new SuspensionDetails(saved, saved.isInForce(today));
    }
}
