package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CheckOutCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.CheckOutReservedCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.BorrowingEligibility;
import com.riverfount.booksmanager.loan.domain.BorrowingSituation;
import com.riverfount.booksmanager.loan.domain.CopyHold;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers the loan of a copy to a reader (RF11, HU07). It only orchestrates: gathers the
 * facts (the copy and the reader through the public ports of their modules, the loans, fines,
 * suspensions and the reservation that holds the copy, if any, through this module's repositories,
 * the date from the clock), lets the domain decide ({@link BorrowingEligibility}, {@link Loan#create})
 * and persists. It is one transaction (RNF16): if marking the copy or saving the loan fails, nothing stays;
 * and two requests for the same copy at the same time do not both pass (RNF17), because the copy
 * is versioned and the database allows only one active loan per copy.
 *
 * <p>A copy kept for a reservation (RF18) is lent only to the owner of the reservation, up to the pickup
 * deadline (RN03, RN13): then, in the same transaction, the copy goes from {@code RESERVED} to
 * {@code BORROWED} and the reservation is fulfilled. The other rules (limit, fine, suspension, late loan,
 * reader) still apply to the owner.
 */
@Service
@Transactional
class RegisterLoanService implements RegisterLoanUseCase {

    private static final PageQuery JUST_THE_COUNT = PageQuery.of(0, 1, "id");

    private final LibraryConfigurationRepository configurationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final SuspensionRepository suspensionRepository;
    private final FindCopyAvailabilityUseCase copyAvailability;
    private final CheckOutCopyUseCase checkOutCopy;
    private final CheckOutReservedCopyUseCase checkOutReservedCopy;
    private final ReservationRepository reservationRepository;
    private final GetReaderBorrowingStateUseCase readerState;
    private final Clock clock;

    RegisterLoanService(LibraryConfigurationRepository configurationRepository, LoanRepository loanRepository,
            FineRepository fineRepository, SuspensionRepository suspensionRepository,
            FindCopyAvailabilityUseCase copyAvailability, CheckOutCopyUseCase checkOutCopy,
            CheckOutReservedCopyUseCase checkOutReservedCopy, ReservationRepository reservationRepository,
            GetReaderBorrowingStateUseCase readerState, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.loanRepository = loanRepository;
        this.fineRepository = fineRepository;
        this.suspensionRepository = suspensionRepository;
        this.copyAvailability = copyAvailability;
        this.checkOutCopy = checkOutCopy;
        this.checkOutReservedCopy = checkOutReservedCopy;
        this.reservationRepository = reservationRepository;
        this.readerState = readerState;
        this.clock = clock;
    }

    @Override
    public Loan register(RegisterLoanCommand command) {
        var today = LocalDate.now(clock);
        var configuration = configurationRepository.get();
        // built first: this way the ids are guaranteed non-null when the other modules are asked
        var loan = Loan.create(command.copyId(), command.readerId(), today, configuration.getLoanPeriodDays());

        var copy = copyAvailability.findAvailability(loan.getCopyId())
                .orElseThrow(() -> new ResourceNotFoundException("exemplar não encontrado: " + loan.getCopyId()));
        var holding = copy.reserved() ? reservationRepository.findActiveByCopyId(loan.getCopyId()).orElse(null) : null;
        var hold = copy.reserved() ? CopyHold.of(holding, loan.getReaderId(), today) : CopyHold.NONE;

        BorrowingEligibility.check(situationOf(loan, copy.lendable(), copy.bookActive(), hold, today), configuration.getLoanLimit());

        if (hold == CopyHold.FOR_THIS_READER) {
            pickUpReservedCopy(loan, holding, today);
        } else {
            checkOutCopy.checkOut(loan.getCopyId());
        }
        return loanRepository.save(loan);
    }

    /** The owner takes the copy kept for them: the copy is borrowed and the reservation is fulfilled (RF17). */
    private void pickUpReservedCopy(Loan loan, Reservation reservation, LocalDate today) {
        checkOutReservedCopy.checkOutReserved(loan.getCopyId());
        reservation.fulfill(today);
        reservationRepository.save(reservation);
    }

    private BorrowingSituation situationOf(Loan loan, boolean copyLendable, boolean bookActive, CopyHold hold,
            LocalDate today) {
        var reader = readerState.getBorrowingState(loan.getReaderId());
        var readerId = loan.getReaderId();
        var activeLoans = loanRepository.findAll(new LoanFilter(readerId, null, true, null), JUST_THE_COUNT)
                .totalItems();
        var overdueLoans = loanRepository.findAll(new LoanFilter(readerId, null, null, today), JUST_THE_COUNT)
                .totalItems();
        var pendingFines = fineRepository.findAll(new FineFilter(readerId, null, FineStatus.PENDING), JUST_THE_COUNT)
                .totalItems();
        var suspensions = suspensionRepository.findAll(new SuspensionFilter(readerId, null, today, null), JUST_THE_COUNT)
                .totalItems();
        return new BorrowingSituation(copyLendable, hold, bookActive, reader.active(), reader.blocked(),
                Math.toIntExact(activeLoans), pendingFines > 0, suspensions > 0, overdueLoans > 0);
    }
}
