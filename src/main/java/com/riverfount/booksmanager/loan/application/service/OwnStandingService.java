package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.GetOwnStandingQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnStandingUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReaderStanding;
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
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells a reader how they stand (Phase 6, RF10, RF20, RF21): it only gathers the facts, as the
 * {@link RegisterLoanService} does (the reader through the public port of the user module, the loans, fines,
 * suspensions and reservations through this module's repositories, the day from the clock) and lets the domain say why
 * the reader could not borrow ({@link BorrowingEligibility}), so the reasons are exactly the ones a loan at the desk
 * would be refused for and no rule is repeated here. The facts of a copy are left out by giving the domain a copy that
 * is lendable, so only what concerns the reader can refuse. The number of queries is fixed, whatever the reader owes.
 */
@Service
@Transactional(readOnly = true)
class OwnStandingService implements GetOwnStandingUseCase {

    private static final PageQuery JUST_THE_COUNT = PageQuery.of(0, 1, "id");
    private static final PageQuery ALL_IN_FORCE = PageQuery.of(0, PageQuery.MAX_SIZE, "id");

    private final LibraryConfigurationRepository configurationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final SuspensionRepository suspensionRepository;
    private final ReservationRepository reservationRepository;
    private final GetReaderBorrowingStateUseCase readerState;
    private final Clock clock;

    OwnStandingService(LibraryConfigurationRepository configurationRepository, LoanRepository loanRepository,
            FineRepository fineRepository, SuspensionRepository suspensionRepository,
            ReservationRepository reservationRepository, GetReaderBorrowingStateUseCase readerState, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.loanRepository = loanRepository;
        this.fineRepository = fineRepository;
        this.suspensionRepository = suspensionRepository;
        this.reservationRepository = reservationRepository;
        this.readerState = readerState;
        this.clock = clock;
    }

    @Override
    public ReaderStanding get(GetOwnStandingQuery query) {
        var readerId = query.readerId();
        var today = LocalDate.now(clock);
        var loanLimit = configurationRepository.get().getLoanLimit();

        var reader = readerState.getBorrowingState(readerId);
        var activeLoans = loanRepository.findAll(new LoanFilter(readerId, null, true, null), JUST_THE_COUNT)
                .totalItems();
        var overdueLoans = loanRepository.findAll(new LoanFilter(readerId, null, null, today), JUST_THE_COUNT)
                .totalItems();
        var pendingFines = fineRepository.findAll(new FineFilter(readerId, null, FineStatus.PENDING), JUST_THE_COUNT)
                .totalItems();
        var inForce = suspensionRepository.findAll(new SuspensionFilter(readerId, null, today, null), ALL_IN_FORCE)
                .items();

        // a lendable copy of an active book: only the facts about the reader can refuse
        var situation = new BorrowingSituation(true, CopyHold.NONE, true, reader.active(), reader.blocked(),
                Math.toIntExact(activeLoans), pendingFines > 0, !inForce.isEmpty(), overdueLoans > 0);
        var reasons = BorrowingEligibility.refusals(situation, loanLimit);

        return new ReaderStanding(reasons.isEmpty(), reader.blocked(), reasons,
                fineRepository.sumPendingAmount(readerId), suspensionOf(inForce), activeLoans, overdueLoans,
                loanLimit, reservationRepository.countActiveByReader(readerId), loanLimit);
    }

    /** The suspension in force, from the earliest start to the latest release when there are more than one. */
    private static ReaderStanding.SuspensionInForce suspensionOf(List<Suspension> inForce) {
        if (inForce.isEmpty()) {
            return null;
        }
        var start = inForce.stream().map(Suspension::getStartDate).min(Comparator.naturalOrder()).orElseThrow();
        var release = inForce.stream().map(Suspension::releaseDate).max(Comparator.naturalOrder()).orElseThrow();
        return new ReaderStanding.SuspensionInForce(start, release);
    }
}
