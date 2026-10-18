package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CheckInCopyUseCase;
import com.riverfount.booksmanager.catalog.domain.CopyReturnCondition;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanResult;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.ReturnCondition;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a loan (RF12, RF20, HU08). It only orchestrates: loads the loan and the
 * configuration, lets the domain close the loan and count the days late
 * ({@code Loan.returnCopy}) and calculate the penalty with the policy of the moment
 * ({@code PenaltyPolicy.calculate}, RN08, RN16; it does not depend on the condition of the copy, RF15),
 * records the return of the copy through the catalog's public port (available again, or damaged or lost) and persists the loan and the fine and/or suspension. It is one
 * transaction (RNF16): if any step fails, nothing stays. A return already registered is never
 * recalculated, because the penalty is stored when the loan is returned.
 */
@Service
@Transactional
class ReturnLoanService implements ReturnLoanUseCase {

    private final LibraryConfigurationRepository configurationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final SuspensionRepository suspensionRepository;
    private final CheckInCopyUseCase checkInCopy;
    private final Clock clock;

    ReturnLoanService(LibraryConfigurationRepository configurationRepository, LoanRepository loanRepository,
            FineRepository fineRepository, SuspensionRepository suspensionRepository,
            CheckInCopyUseCase checkInCopy, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.loanRepository = loanRepository;
        this.fineRepository = fineRepository;
        this.suspensionRepository = suspensionRepository;
        this.checkInCopy = checkInCopy;
        this.clock = clock;
    }

    @Override
    public ReturnLoanResult returnLoan(ReturnLoanCommand command) {
        var today = LocalDate.now(clock);
        var loan = loanRepository.findById(command.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("empréstimo não encontrado: " + command.loanId()));
        var configuration = configurationRepository.get();

        var daysLate = loan.returnCopy(today, command.condition());
        var penalty = configuration.getPenaltyPolicy().calculate(daysLate, configuration);

        checkInCopy.checkIn(loan.getCopyId(), catalogConditionOf(command.condition()));
        var savedLoan = loanRepository.save(loan);
        var fine = penalty.hasFine()
                ? fineRepository.save(Fine.create(loan.getId(), loan.getReaderId(), penalty.fineAmount()))
                : null;
        var suspension = penalty.hasSuspension()
                ? suspensionRepository.save(
                        Suspension.create(loan.getReaderId(), loan.getId(), today, penalty.suspensionDays()))
                : null;
        return new ReturnLoanResult(savedLoan, daysLate, fine, suspension);
    }

    /** The loan and the catalog each have their own type for the condition; this is where they meet. */
    private static CopyReturnCondition catalogConditionOf(ReturnCondition condition) {
        return switch (condition) {
            case GOOD -> CopyReturnCondition.GOOD;
            case DAMAGED -> CopyReturnCondition.DAMAGED;
            case LOST -> CopyReturnCondition.LOST;
        };
    }
}
