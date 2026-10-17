package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renews an active loan (RF13, HU09). It only orchestrates: loads the loan and the
 * configuration, lets the domain decide and apply the renewal ({@link Loan#renew}: closed
 * loan, late loan and the renewal limit are refused there, RN07, RN09) and persists. The
 * limit and the period added come from the configuration (RN16).
 *
 * <p>Extension point (phase 4): RN07 also refuses a renewal when another reader has a
 * pending reservation for the book. Reservations do not exist yet, so nothing is checked
 * here; when they arrive, the fact "the book has a pending reservation" is fetched by this
 * service through a port and handed to the domain, like the facts of the loan are.
 */
@Service
@Transactional
class RenewLoanService implements RenewLoanUseCase {

    private final LibraryConfigurationRepository configurationRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    RenewLoanService(LibraryConfigurationRepository configurationRepository, LoanRepository loanRepository,
            Clock clock) {
        this.configurationRepository = configurationRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    @Override
    public Loan renew(RenewLoanCommand command) {
        var loan = loanRepository.findById(command.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("empréstimo não encontrado: " + command.loanId()));
        var configuration = configurationRepository.get();

        loan.renew(LocalDate.now(clock), configuration.getRenewalLimit(), configuration.getLoanPeriodDays());
        return loanRepository.save(loan);
    }
}
