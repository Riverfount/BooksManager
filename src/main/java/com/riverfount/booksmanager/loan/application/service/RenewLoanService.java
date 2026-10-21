package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renews an active loan (RF13, HU09). It only orchestrates: loads the loan and the
 * configuration, finds out whether the book of the copy has a reservation waiting in its queue
 * (the book through the public port of the catalog, the queue through the reservation repository),
 * lets the domain decide and apply the renewal ({@link Loan#renew}: closed loan, late loan, a waiting
 * reservation and the renewal limit are refused there, RN07, RN09) and persists. The limit and the
 * period added come from the configuration (RN16). Only a reservation that waits in the queue blocks
 * the renewal; one that already holds a copy does not (RN07).
 */
@Service
@Transactional
class RenewLoanService implements RenewLoanUseCase {

    private final LibraryConfigurationRepository configurationRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final FindCopySummariesUseCase copySummaries;
    private final Clock clock;

    RenewLoanService(LibraryConfigurationRepository configurationRepository, LoanRepository loanRepository,
            ReservationRepository reservationRepository, FindCopySummariesUseCase copySummaries, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.loanRepository = loanRepository;
        this.reservationRepository = reservationRepository;
        this.copySummaries = copySummaries;
        this.clock = clock;
    }

    @Override
    public Loan renew(RenewLoanCommand command) {
        var loan = loanRepository.findById(command.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("empréstimo não encontrado: " + command.loanId()));
        var configuration = configurationRepository.get();

        loan.renew(LocalDate.now(clock), configuration.getRenewalLimit(), configuration.getLoanPeriodDays(),
                bookHasWaitingReservation(loan));
        return loanRepository.save(loan);
    }

    /** Whether the book of the loan's copy has a reservation waiting in the queue for a copy. */
    private boolean bookHasWaitingReservation(Loan loan) {
        var copy = copySummaries.findSummaries(Set.of(loan.getCopyId())).get(loan.getCopyId());
        return copy != null && reservationRepository.findFirstWaiting(copy.bookId()).isPresent();
    }
}
