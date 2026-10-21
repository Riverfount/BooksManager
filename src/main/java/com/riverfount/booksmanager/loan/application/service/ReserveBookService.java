package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.FindBookAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookResult;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookUseCase;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.ReservationFilter;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationEligibility;
import com.riverfount.booksmanager.loan.domain.ReservationSituation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves a book for a reader (RF16). It only orchestrates: gathers the facts (the book through the
 * public port of the catalog, the reader through the one of the user module, the loans, fines,
 * suspensions and reservations of the reader through this module's repositories, the date from the
 * clock), lets the domain decide ({@link ReservationEligibility}, {@link Reservation#create}) and
 * persists. It is one transaction (RNF16). Two requests of the same reader for the same book at the
 * same time do not both pass (RNF17): the partial unique index of the database lets only one reservation
 * stay, and the adapter turns the other violation into the same refusal as a repeated request.
 */
@Service
@Transactional
class ReserveBookService implements ReserveBookUseCase {

    private static final PageQuery JUST_THE_COUNT = PageQuery.of(0, 1, "id");

    private final LibraryConfigurationRepository configurationRepository;
    private final ReservationRepository reservationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final SuspensionRepository suspensionRepository;
    private final FindBookAvailabilityUseCase bookAvailability;
    private final FindCopySummariesUseCase copySummaries;
    private final GetReaderBorrowingStateUseCase readerState;
    private final Clock clock;

    ReserveBookService(LibraryConfigurationRepository configurationRepository,
            ReservationRepository reservationRepository, LoanRepository loanRepository,
            FineRepository fineRepository, SuspensionRepository suspensionRepository,
            FindBookAvailabilityUseCase bookAvailability, FindCopySummariesUseCase copySummaries,
            GetReaderBorrowingStateUseCase readerState, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.reservationRepository = reservationRepository;
        this.loanRepository = loanRepository;
        this.fineRepository = fineRepository;
        this.suspensionRepository = suspensionRepository;
        this.bookAvailability = bookAvailability;
        this.copySummaries = copySummaries;
        this.readerState = readerState;
        this.clock = clock;
    }

    @Override
    public ReserveBookResult reserve(ReserveBookCommand command) {
        var today = LocalDate.now(clock);
        var configuration = configurationRepository.get();
        // built first: this way the ids are guaranteed non-null when the other modules are asked
        var reservation = Reservation.create(command.bookId(), command.readerId(), today);

        // the limit of reservations is the loan limit of the configuration (RN12, RN04)
        ReservationEligibility.check(situationOf(reservation, today), configuration.getLoanLimit());

        var saved = reservationRepository.save(reservation);
        var position = reservationRepository.queuePosition(saved.getId()).orElseThrow(
                () -> new IllegalStateException("the reservation " + saved.getId() + " is not in the queue"));
        return new ReserveBookResult(saved, position);
    }

    private ReservationSituation situationOf(Reservation reservation, LocalDate today) {
        var bookId = reservation.getBookId();
        var readerId = reservation.getReaderId();
        var book = bookAvailability.findBookAvailability(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("livro não encontrado: " + bookId));
        var reader = readerState.getBorrowingState(readerId);
        var activeReservations = reservationRepository.countActiveByReader(readerId);
        var sameBookReserved = reservationRepository
                .findAll(new ReservationFilter(readerId, bookId, ReservationStatus.ACTIVE), JUST_THE_COUNT)
                .totalItems();
        var overdueLoans = loanRepository.count(new LoanFilter(readerId, null, null, today));
        var pendingFines = fineRepository.findAll(new FineFilter(readerId, null, FineStatus.PENDING), JUST_THE_COUNT)
                .totalItems();
        var suspensions = suspensionRepository.findAll(new SuspensionFilter(readerId, null, today, null), JUST_THE_COUNT)
                .totalItems();
        return new ReservationSituation(reader.active(), reader.blocked(), Math.toIntExact(activeReservations),
                sameBookReserved > 0, book.hasAvailableCopy(), hasOnLoan(readerId, bookId), pendingFines > 0,
                suspensions > 0, overdueLoans > 0);
    }

    /** Whether the reader holds a copy of this book on loan: the copies of the open loans, taken to their books. */
    private boolean hasOnLoan(Long readerId, Long bookId) {
        var copyIds = loanRepository.findActiveCopyIdsByReader(readerId);
        if (copyIds.isEmpty()) {
            return false;
        }
        return copySummaries.findSummaries(copyIds).values().stream()
                .map(CopySummary::bookId).anyMatch(bookId::equals);
    }
}
