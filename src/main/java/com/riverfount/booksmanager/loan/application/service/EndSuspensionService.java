package com.riverfount.booksmanager.loan.application.service;

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
 * seeing it (RN05) without anything else being touched.
 */
@Service
@Transactional
class EndSuspensionService implements EndSuspensionUseCase {

    private final SuspensionRepository suspensionRepository;
    private final Clock clock;

    EndSuspensionService(SuspensionRepository suspensionRepository, Clock clock) {
        this.suspensionRepository = suspensionRepository;
        this.clock = clock;
    }

    @Override
    public SuspensionDetails end(EndSuspensionCommand command) {
        var today = LocalDate.now(clock);
        var suspension = suspensionRepository.findById(command.suspensionId()).orElseThrow(
                () -> new ResourceNotFoundException("suspensão não encontrada: " + command.suspensionId()));

        var saved = suspension.endEarly(today, command.reason()) ? suspensionRepository.save(suspension) : suspension;
        return new SuspensionDetails(saved, saved.isInForce(today));
    }
}
