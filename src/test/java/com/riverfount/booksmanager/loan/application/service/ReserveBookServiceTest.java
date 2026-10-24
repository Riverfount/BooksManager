package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.catalog.application.port.in.BookAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindBookAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.out.ReservationFilter;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderBorrowingState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reserving a book (RF16): the service only orchestrates; the rules are the domain's (RN05, RN11,
 * RN12, RN16). The reservation goes to the end of the queue of the book.
 */
class ReserveBookServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long BOOK = 30L;
    private static final long READER = 20L;

    private InMemoryLibraryConfigurationRepository configurationRepository;
    private InMemoryReservationRepository reservationRepository;
    private InMemoryLoanRepository loanRepository;
    private InMemoryFineRepository fineRepository;
    private InMemorySuspensionRepository suspensionRepository;
    private FindBookAvailabilityUseCase bookAvailability;
    private FindCopySummariesUseCase copySummaries;
    private GetReaderBorrowingStateUseCase readerState;
    private ReserveBookService service;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        reservationRepository = new InMemoryReservationRepository();
        loanRepository = new InMemoryLoanRepository();
        fineRepository = new InMemoryFineRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        bookAvailability = mock(FindBookAvailabilityUseCase.class);
        copySummaries = mock(FindCopySummariesUseCase.class);
        readerState = mock(GetReaderBorrowingStateUseCase.class);
        service = new ReserveBookService(configurationRepository, reservationRepository, loanRepository,
                fineRepository, suspensionRepository, bookAvailability, copySummaries, readerState, CLOCK);
        bookHasAvailableCopy(false);
        readerIs(true, false);
    }

    private void bookHasAvailableCopy(boolean available) {
        given(bookAvailability.findBookAvailability(BOOK))
                .willReturn(Optional.of(new BookAvailability(BOOK, available, true)));
    }

    private void bookIsInactive() {
        given(bookAvailability.findBookAvailability(BOOK))
                .willReturn(Optional.of(new BookAvailability(BOOK, false, false)));
    }

    private void readerIs(boolean active, boolean blocked) {
        given(readerState.getBorrowingState(READER))
                .willReturn(new ReaderBorrowingState(READER, active, blocked, active && !blocked));
    }

    private com.riverfount.booksmanager.loan.application.port.in.ReserveBookResult reserve() {
        return service.reserve(new ReserveBookCommand(BOOK, READER));
    }

    private long storedReservations() {
        return reservationRepository.findAll(ReservationFilter.none(), PageQuery.of(0, 100, "id")).totalItems();
    }

    private void reserveFor(long book, long reader) {
        reservationRepository.save(Reservation.create(book, reader, TODAY));
    }

    private void loanOfTheReader(long copy, long book, LocalDate pickup) {
        loanRepository.save(Loan.create(copy, READER, pickup, 14));
        given(copySummaries.findSummaries(Set.of(copy)))
                .willReturn(Map.of(copy, new CopySummary(copy, "PAT-" + copy, book, "Title")));
    }

    // the reservation (RF16)

    @Test
    void shouldReserveTheBookAsActiveAndWaitingFromToday() {
        var result = reserve();

        var reservation = result.reservation();
        assertThat(reservation.getId()).isNotNull();
        assertThat(reservation.getBookId()).isEqualTo(BOOK);
        assertThat(reservation.getReaderId()).isEqualTo(READER);
        assertThat(reservation.getReservationDate()).isEqualTo(TODAY);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.isWaiting()).isTrue();
        assertThat(reservationRepository.findById(reservation.getId())).isPresent();
    }

    @Test
    void shouldUseTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 11th is still the 10th in São Paulo
        var lateNight = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZONE);
        var lateService = new ReserveBookService(configurationRepository, reservationRepository, loanRepository,
                fineRepository, suspensionRepository, bookAvailability, copySummaries, readerState, lateNight);

        var result = lateService.reserve(new ReserveBookCommand(BOOK, READER));

        assertThat(result.reservation().getReservationDate()).isEqualTo(TODAY);
    }

    @Test
    void theFirstReservationOfABookIsInThePositionOne() {
        assertThat(reserve().queuePosition()).isEqualTo(1L);
    }

    @Test
    void theReservationShouldGoToTheEndOfTheQueueOfTheBook() {
        reserveFor(BOOK, 91L);
        reserveFor(BOOK, 92L);
        reserveFor(99L, 93L);

        var result = reserve();

        assertThat(result.queuePosition()).isEqualTo(3L);
        assertThat(reservationRepository.queuePosition(result.reservation().getId())).contains(3L);
    }

    @Test
    void theReservationsOfOtherBooksAndClosedOnesDoNotCountInTheQueue() {
        var cancelled = reservationRepository.save(Reservation.create(BOOK, 91L, TODAY));
        cancelled.cancel();
        reservationRepository.save(cancelled);
        reserveFor(99L, 92L);

        assertThat(reserve().queuePosition()).isEqualTo(1L);
    }

    // the refusals (RN05, RN11, RN12)

    @Test
    void shouldRefuseTheReservationOfAnInactiveBook() {
        bookIsInactive();

        assertThatThrownBy(() -> service.reserve(new ReserveBookCommand(BOOK, READER)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o livro está inativo");

        assertThat(reservationRepository.countActiveByReader(READER)).isZero();
    }

    @Test
    void shouldRefuseAnInactiveReader() {
        readerIs(false, false);

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o leitor está inativo");
    }

    @Test
    void shouldRefuseABlockedReader() {
        readerIs(true, true);

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o leitor está bloqueado");
    }

    @Test
    void shouldRefuseABookThatHasAnAvailableCopy() {
        bookHasAvailableCopy(true);

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar disponível");
    }

    @Test
    void shouldRefuseABookTheReaderAlreadyHasOnLoan() {
        loanOfTheReader(101L, BOOK, TODAY);

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já tem este livro emprestado");
    }

    @Test
    void aLoanOfAnotherBookDoesNotStopTheReservation() {
        loanOfTheReader(101L, 99L, TODAY);

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void aReturnedLoanOfTheBookDoesNotStopTheReservation() {
        var returned = loanRepository.save(Loan.create(101L, READER, TODAY.minusDays(30), 14));
        returned.returnCopy(TODAY.minusDays(20));
        loanRepository.save(returned);

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void shouldRefuseTheSameBookTwice() {
        reserveFor(BOOK, READER);

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reserva ativa deste livro");
        assertThat(storedReservations()).isEqualTo(1);
    }

    @Test
    void shouldAllowTheBookAgainOnceThePreviousReservationWasClosed() {
        var previous = reservationRepository.save(Reservation.create(BOOK, READER, TODAY.minusDays(5)));
        previous.cancel();
        reservationRepository.save(previous);

        assertThat(reserve().reservation().getId()).isNotEqualTo(previous.getId());
    }

    @Test
    void shouldRefuseAReaderWhoReachedTheLimitOfActiveReservations() {
        reserveFor(101L, READER);
        reserveFor(102L, READER);
        reserveFor(103L, READER);

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de 3 reservas ativas");
    }

    @Test
    void shouldAllowAReaderOneReservationBelowTheLimit() {
        reserveFor(101L, READER);
        reserveFor(102L, READER);

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void theLimitIsTheLoanLimitOfTheConfigurationNotAConstant() {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, null, 1, null, null, null);
        configurationRepository.save(configuration);
        reserveFor(101L, READER);

        assertThatThrownBy(this::reserve).hasMessageContaining("limite de 1 reserva ativa");
    }

    @Test
    void theReservationsAreCountedApartFromTheLoans() {
        // three loans of other books fill the loan limit, and the reader can still reserve
        loanRepository.save(Loan.create(101L, READER, TODAY, 14));
        loanRepository.save(Loan.create(102L, READER, TODAY, 14));
        loanRepository.save(Loan.create(103L, READER, TODAY, 14));
        given(copySummaries.findSummaries(Set.of(101L, 102L, 103L))).willReturn(Map.of(
                101L, new CopySummary(101L, "P-101", 91L, "A"), 102L, new CopySummary(102L, "P-102", 92L, "B"),
                103L, new CopySummary(103L, "P-103", 93L, "C")));

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void reservationsOfOtherReadersAndClosedOnesDoNotCountForTheLimit() {
        reserveFor(101L, 91L);
        reserveFor(102L, 91L);
        reserveFor(103L, 91L);
        var closed = reservationRepository.save(Reservation.create(104L, READER, TODAY));
        closed.cancel();
        reservationRepository.save(closed);

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void shouldRefuseAReaderWithAPendingFine() {
        fineRepository.save(Fine.create(55L, READER, new BigDecimal("3.00")));

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("multa pendente");
    }

    @Test
    void shouldAllowAReaderWhoseFinesWerePaidOrWaived() {
        var paid = fineRepository.save(Fine.create(55L, READER, new BigDecimal("3.00")));
        paid.pay();
        fineRepository.save(paid);
        var waived = fineRepository.save(Fine.create(56L, READER, new BigDecimal("2.00")));
        waived.waive("Hospital stay");
        fineRepository.save(waived);

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void shouldRefuseASuspendedReader() {
        suspensionRepository.save(Suspension.create(READER, 55L, TODAY.minusDays(1), 3));

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o leitor está suspenso");
    }

    @Test
    void shouldAllowAReaderWhoseSuspensionEndedOrWasEndedEarly() {
        suspensionRepository.save(Suspension.create(READER, 55L, TODAY.minusDays(10), 3));
        var early = suspensionRepository.save(Suspension.create(READER, 56L, TODAY.minusDays(1), 30));
        early.endEarly(TODAY, "Pardoned");
        suspensionRepository.save(early);

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void shouldRefuseAReaderWithALateLoan() {
        loanOfTheReader(101L, 99L, TODAY.minusDays(20));

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo em atraso");
    }

    @Test
    void shouldAllowAReaderWhoseLoanIsDueTodayAndSoNotLate() {
        loanOfTheReader(101L, 99L, TODAY.minusDays(14));

        assertThat(reserve().reservation().getId()).isNotNull();
    }

    @Test
    void shouldTellEveryReasonAtOnce() {
        bookHasAvailableCopy(true);
        readerIs(true, true);
        fineRepository.save(Fine.create(55L, READER, new BigDecimal("3.00")));

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessage("A reserva não pode ser feita: o leitor está bloqueado; "
                        + "o livro tem exemplar disponível, então deve ser feito o empréstimo; "
                        + "o leitor tem multa pendente.");
    }

    @Test
    void aRefusedReservationShouldChangeNothing() {
        bookHasAvailableCopy(true);

        assertThatThrownBy(this::reserve).isInstanceOf(BusinessRuleException.class);

        assertThat(storedReservations()).isZero();
    }

    // things that do not exist and bad input

    @Test
    void shouldFailWithNotFoundForABookThatDoesNotExist() {
        given(bookAvailability.findBookAvailability(BOOK)).willReturn(Optional.empty());

        assertThatThrownBy(this::reserve).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(BOOK));
        assertThat(storedReservations()).isZero();
    }

    @Test
    void shouldLetTheNotFoundOfTheReaderPass() {
        given(readerState.getBorrowingState(READER))
                .willThrow(new ResourceNotFoundException("leitor não encontrado: 20"));

        assertThatThrownBy(this::reserve).isInstanceOf(ResourceNotFoundException.class);
        assertThat(storedReservations()).isZero();
    }

    @Test
    void shouldRequireTheBookAndTheReader() {
        assertThatThrownBy(() -> service.reserve(new ReserveBookCommand(null, READER)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reserve(new ReserveBookCommand(BOOK, null)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
