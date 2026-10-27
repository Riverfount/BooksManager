package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CopyAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.RenewalEligibility;
import com.riverfount.booksmanager.loan.domain.RenewalSituation;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renews an active loan (RF13, HU09). It only orchestrates: loads the loan and the
 * configuration, finds out whether the book of the copy has a reservation waiting in its queue
 * (the book through the public port of the catalog, the queue through the reservation repository),
 * lets the domain decide whether the reader may renew by their own state ({@link RenewalEligibility}: inactive,
 * blocked, a pending fine or a suspension in force, RN21, all the reasons at once, for the desk and for the reader alike),
 * then decide and apply the renewal ({@link Loan#renew}: closed loan, late loan, a waiting
 * reservation and the renewal limit are refused there, RN07, RN09) and persists. The limit and the
 * period added come from the configuration (RN16). The book of the copy must still be active (RN14: the
 * copy can be returned, but the loan of a book that was inactivated is not extended). Only a reservation that waits in the queue blocks
 * the renewal; one that already holds a copy does not (RN07).
 */
@Service
@Transactional
class RenewLoanService implements RenewLoanUseCase {

    private final LibraryConfigurationRepository configurationRepository;
    private final LoanRepository loanRepository;
    private static final PageQuery JUST_THE_COUNT = PageQuery.of(0, 1, "id");

    private final ReservationRepository reservationRepository;
    private final FineRepository fineRepository;
    private final SuspensionRepository suspensionRepository;
    private final FindCopySummariesUseCase copySummaries;
    private final FindCopyAvailabilityUseCase copyAvailability;
    private final GetReaderBorrowingStateUseCase readerState;
    private final Clock clock;

    RenewLoanService(LibraryConfigurationRepository configurationRepository, LoanRepository loanRepository,
            ReservationRepository reservationRepository, FineRepository fineRepository,
            SuspensionRepository suspensionRepository, FindCopySummariesUseCase copySummaries,
            FindCopyAvailabilityUseCase copyAvailability, GetReaderBorrowingStateUseCase readerState, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.loanRepository = loanRepository;
        this.reservationRepository = reservationRepository;
        this.copySummaries = copySummaries;
        this.copyAvailability = copyAvailability;
        this.fineRepository = fineRepository;
        this.suspensionRepository = suspensionRepository;
        this.readerState = readerState;
        this.clock = clock;
    }

    @Override
    public Loan renew(RenewLoanCommand command) {
        var loan = loanRepository.findById(command.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("empréstimo não encontrado: " + command.loanId()));
        var configuration = configurationRepository.get();

        var today = LocalDate.now(clock);

        RenewalEligibility.check(readerSituation(loan, today));
        loan.renew(today, configuration.getRenewalLimit(), configuration.getLoanPeriodDays(),
                bookHasWaitingReservation(loan), bookIsActive(loan));
        return loanRepository.save(loan);
    }

    /** The state of the reader of the loan, today: the reader through the port of the user module, the rest here. */
    private RenewalSituation readerSituation(Loan loan, LocalDate today) {
        var readerId = loan.getReaderId();
        var reader = readerState.getBorrowingState(readerId);
        var pendingFines = fineRepository.findAll(new FineFilter(readerId, null, FineStatus.PENDING), JUST_THE_COUNT)
                .totalItems();
        var suspensions = suspensionRepository.findAll(new SuspensionFilter(readerId, null, today, null), JUST_THE_COUNT)
                .totalItems();
        return new RenewalSituation(reader.active(), reader.blocked(), pendingFines > 0, suspensions > 0);
    }

    /** Whether the book of the loan's copy is still active (RN14); a copy that is not found blocks nothing. */
    private boolean bookIsActive(Loan loan) {
        return copyAvailability.findAvailability(loan.getCopyId()).map(CopyAvailability::bookActive).orElse(true);
    }

    /** Whether the book of the loan's copy has a reservation waiting in the queue for a copy. */
    private boolean bookHasWaitingReservation(Loan loan) {
        var copy = copySummaries.findSummaries(Set.of(loan.getCopyId())).get(loan.getCopyId());
        return copy != null && reservationRepository.findFirstWaiting(copy.bookId()).isPresent();
    }
}
