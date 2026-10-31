package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.catalog.application.port.in.CheckOutCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.CheckOutReservedCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.CopyAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Registering a loan (RF11, HU07): the service only orchestrates; the rules are the domain's
 * (RN03, RN04, RN05, RN06, RN11, RN16).
 */
class RegisterLoanServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long COPY = 7L;
    private static final long READER = 20L;

    private InMemoryLibraryConfigurationRepository configurationRepository;
    private InMemoryLoanRepository loanRepository;
    private InMemoryFineRepository fineRepository;
    private InMemorySuspensionRepository suspensionRepository;
    private FindCopyAvailabilityUseCase copyAvailability;
    private CheckOutCopyUseCase checkOutCopy;
    private CheckOutReservedCopyUseCase checkOutReservedCopy;
    private InMemoryReservationRepository reservationRepository;
    private GetReaderBorrowingStateUseCase readerState;
    private RegisterLoanService service;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        loanRepository = new InMemoryLoanRepository();
        fineRepository = new InMemoryFineRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        copyAvailability = mock(FindCopyAvailabilityUseCase.class);
        checkOutCopy = mock(CheckOutCopyUseCase.class);
        checkOutReservedCopy = mock(CheckOutReservedCopyUseCase.class);
        reservationRepository = new InMemoryReservationRepository();
        readerState = mock(GetReaderBorrowingStateUseCase.class);
        service = new RegisterLoanService(configurationRepository, loanRepository, fineRepository,
                suspensionRepository, copyAvailability, checkOutCopy, checkOutReservedCopy, reservationRepository,
                readerState, CLOCK);
        copyIs(true);
        readerIs(true, false);
    }

    private void copyIs(boolean lendable) {
        given(copyAvailability.findAvailability(COPY)).willReturn(Optional.of(new CopyAvailability(COPY, lendable, false, true)));
    }

    private void copyOfAnInactiveBook() {
        given(copyAvailability.findAvailability(COPY)).willReturn(Optional.of(new CopyAvailability(COPY, true, false, false)));
    }

    private void readerIs(boolean active, boolean blocked) {
        given(readerState.getBorrowingState(READER))
                .willReturn(new ReaderBorrowingState(READER, active, blocked, active && !blocked));
    }

    private Loan register() {
        return service.register(new RegisterLoanCommand(COPY, READER));
    }

    private int storedLoans() {
        return (int) loanRepository.findAll(LoanFilter.none(), PageQuery.of(0, 100, "id")).totalItems();
    }

    private void openLoanOfTheReader(long copy, LocalDate pickup) {
        loanRepository.save(Loan.create(copy, READER, pickup, 14));
    }

    // the loan (RN03, RN06)

    @Test
    void shouldRegisterTheLoanFromTodayWithTheConfiguredPeriod() {
        var loan = register();

        assertThat(loan.getId()).isNotNull();
        assertThat(loan.getCopyId()).isEqualTo(COPY);
        assertThat(loan.getReaderId()).isEqualTo(READER);
        assertThat(loan.getPickupDate()).isEqualTo(TODAY);
        assertThat(loan.getDueDate()).isEqualTo(TODAY.plusDays(14));
        assertThat(loan.isActive()).isTrue();
        assertThat(loanRepository.findById(loan.getId())).isPresent();
    }

    @Test
    void shouldTakeThePeriodFromTheConfigurationNotFromTheCode() {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, 7, null, null, null, null, null);
        configurationRepository.save(configuration);

        assertThat(register().getDueDate()).isEqualTo(TODAY.plusDays(7));
    }

    @Test
    void shouldUseTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 11th is still the 10th in São Paulo
        var lateNight = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZONE);
        var lateService = new RegisterLoanService(configurationRepository, loanRepository, fineRepository,
                suspensionRepository, copyAvailability, checkOutCopy, checkOutReservedCopy, reservationRepository,
                readerState, lateNight);

        assertThat(lateService.register(new RegisterLoanCommand(COPY, READER)).getPickupDate()).isEqualTo(TODAY);
    }

    @Test
    void shouldMarkTheCopyAsBorrowed() {
        register();

        then(checkOutCopy).should().checkOut(COPY);
    }

    // a book that was inactivated (RN14): no new loan, not even the pickup of a reserved copy

    @Test
    void shouldNotLendACopyOfAnInactiveBook() {
        copyOfAnInactiveBook();

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o livro está inativo");

        then(checkOutCopy).should(never()).checkOut(anyLong());
        assertThat(storedLoans()).isZero();
    }

    @Test
    void shouldReportTheInactiveBookTogetherWithTheReaderReasons() {
        copyOfAnInactiveBook();
        readerIs(true, true);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o livro está inativo")
                .hasMessageContaining("o leitor está bloqueado");
    }

    @Test
    void theOwnerShouldNotPickUpTheReservedCopyOfAnInactiveBookAndTheReservationStaysAsItIs() {
        var reservation = reservationHoldingTheCopy(READER, TODAY, 3);
        given(copyAvailability.findAvailability(COPY))
                .willReturn(Optional.of(new CopyAvailability(COPY, false, true, false)));

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o livro está inativo");

        then(checkOutReservedCopy).should(never()).checkOutReserved(anyLong());
        then(checkOutCopy).should(never()).checkOut(anyLong());
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().isHoldingCopy()).isTrue();
        assertThat(storedLoans()).isZero();
    }

    // a copy kept for a reservation (RN03, RN13, RF17, RF18)

    private static final long OTHER_READER = 21L;
    private static final long BOOK = 30L;

    private void copyIsReserved() {
        given(copyAvailability.findAvailability(COPY)).willReturn(Optional.of(new CopyAvailability(COPY, false, true, true)));
    }

    /** The copy is kept for {@code owner} until TODAY + days (inclusive), the way the return leaves it. */
    private Reservation reservationHoldingTheCopy(long owner, LocalDate assignedOn, int days) {
        copyIsReserved();
        var reservation = Reservation.create(BOOK, owner, assignedOn.minusDays(1));
        reservation.assignCopy(COPY, assignedOn, days);
        return reservationRepository.save(reservation);
    }

    @Test
    void theOwnerShouldTakeTheReservedCopyAndFulfillTheReservation() {
        var reservation = reservationHoldingTheCopy(READER, TODAY, 3);

        var loan = register();

        assertThat(loan.getCopyId()).isEqualTo(COPY);
        assertThat(loan.getDueDate()).isEqualTo(TODAY.plusDays(14));
        then(checkOutReservedCopy).should().checkOutReserved(COPY);
        then(checkOutCopy).should(never()).checkOut(anyLong());
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.FULFILLED);
        assertThat(reservationRepository.findActiveByCopyId(COPY)).isEmpty();
    }

    @Test
    void theOwnerShouldTakeTheCopyOnTheLastDayOfTheDeadline() {
        reservationHoldingTheCopy(READER, TODAY.minusDays(3), 3); // deadline: today

        assertThat(register()).isNotNull();
    }

    @Test
    void anotherReaderShouldNotTakeAReservedCopy() {
        var reservation = reservationHoldingTheCopy(OTHER_READER, TODAY, 3);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar reservado para outro leitor");

        then(checkOutReservedCopy).should(never()).checkOutReserved(anyLong());
        then(checkOutCopy).should(never()).checkOut(anyLong());
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().isHoldingCopy()).isTrue();
        assertThat(storedLoans()).isZero();
    }

    @Test
    void nobodyShouldTakeTheCopyAfterTheDeadlineNotEvenTheOwnerBeforeTheRoutineExpiresIt() {
        var reservation = reservationHoldingTheCopy(READER, TODAY.minusDays(4), 3); // deadline: yesterday

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prazo de retirada da reserva");

        then(checkOutReservedCopy).should(never()).checkOutReserved(anyLong());
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().isActive()).isTrue();
        assertThat(storedLoans()).isZero();
    }

    @Test
    void aReservedCopyWithNoActiveReservationShouldNotBeLentToAnyone() {
        copyIsReserved();

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar reservado para outro leitor");
    }

    @Test
    void theOwnerShouldStillBeRefusedByTheOtherRules() {
        reservationHoldingTheCopy(READER, TODAY, 3);
        readerIs(true, true);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o leitor está bloqueado")
                .hasMessageNotContaining("exemplar");

        assertThat(reservationRepository.findActiveByCopyId(COPY)).isPresent();
    }

    @Test
    void shouldNotFulfillTheReservationWhenTheCatalogRefusesTheCheckOut() {
        var reservation = reservationHoldingTheCopy(READER, TODAY, 3);
        doThrow(new BusinessRuleException("o exemplar não está reservado")).when(checkOutReservedCopy)
                .checkOutReserved(COPY);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class);

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().isHoldingCopy()).isTrue();
        assertThat(storedLoans()).isZero();
    }

    // the refusals (RN03, RN04, RN05, RN11)

    @Test
    void shouldRefuseACopyThatIsNotAvailable() {
        copyIs(false);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o exemplar não está disponível");
    }

    @Test
    void shouldRefuseAnInactiveReader() {
        readerIs(false, false);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o leitor está inativo");
    }

    @Test
    void shouldRefuseABlockedReader() {
        readerIs(true, true);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o leitor está bloqueado");
    }

    @Test
    void shouldRefuseAReaderWhoReachedTheLimitOfLoans() {
        openLoanOfTheReader(101L, TODAY);
        openLoanOfTheReader(102L, TODAY);
        openLoanOfTheReader(103L, TODAY);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de 3 empréstimos simultâneos");
    }

    @Test
    void shouldAllowAReaderOneLoanBelowTheLimit() {
        openLoanOfTheReader(101L, TODAY);
        openLoanOfTheReader(102L, TODAY);

        assertThat(register()).isNotNull();
    }

    @Test
    void shouldTakeTheLimitFromTheConfiguration() {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, null, 1, null, null, null, null);
        configurationRepository.save(configuration);
        openLoanOfTheReader(101L, TODAY);

        assertThatThrownBy(this::register).hasMessageContaining("limite de 1 empréstimo simultâneo");
    }

    @Test
    void shouldNotCountReturnedLoansNorLoansOfOtherReaders() {
        var returned = loanRepository.save(Loan.create(101L, READER, TODAY.minusDays(30), 14));
        returned.returnCopy(TODAY.minusDays(20));
        loanRepository.save(returned);
        loanRepository.save(Loan.create(102L, 99L, TODAY, 14));
        loanRepository.save(Loan.create(103L, 99L, TODAY, 14));
        loanRepository.save(Loan.create(104L, 99L, TODAY, 14));

        assertThat(register()).isNotNull();
    }

    @Test
    void shouldRefuseAReaderWithAPendingFine() {
        fineRepository.save(Fine.create(55L, READER, new BigDecimal("3.00")));

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
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

        assertThat(register()).isNotNull();
    }

    @Test
    void shouldRefuseASuspendedReader() {
        suspensionRepository.save(Suspension.create(READER, 55L, TODAY.minusDays(1), 3));

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o leitor está suspenso");
    }

    @Test
    void shouldAllowAReaderWhoseSuspensionEndedOrWasEndedEarly() {
        suspensionRepository.save(Suspension.create(READER, 55L, TODAY.minusDays(10), 3));
        var early = suspensionRepository.save(Suspension.create(READER, 56L, TODAY.minusDays(1), 30));
        early.endEarly(TODAY, "Pardoned");
        suspensionRepository.save(early);

        assertThat(register()).isNotNull();
    }

    @Test
    void shouldRefuseAReaderWithALateLoan() {
        openLoanOfTheReader(101L, TODAY.minusDays(20));

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo em atraso");
    }

    @Test
    void shouldAllowAReaderWhoseLoanIsDueTodayAndSoNotLate() {
        openLoanOfTheReader(101L, TODAY.minusDays(14));

        assertThat(register()).isNotNull();
    }

    @Test
    void shouldTellEveryReasonAtOnce() {
        copyIs(false);
        readerIs(true, true);
        fineRepository.save(Fine.create(55L, READER, new BigDecimal("3.00")));

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class)
                .hasMessage("O empréstimo não pode ser feito: o exemplar não está disponível para empréstimo; "
                        + "o leitor está bloqueado; o leitor tem multa pendente.");
    }

    @Test
    void aRefusedLoanShouldChangeNothing() {
        copyIs(false);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class);

        assertThat(storedLoans()).isZero();
        then(checkOutCopy).should(never()).checkOut(anyLong());
    }

    // things that do not exist, bad input and failures (RNF16)

    @Test
    void shouldFailWithNotFoundForACopyThatDoesNotExist() {
        given(copyAvailability.findAvailability(COPY)).willReturn(Optional.empty());

        assertThatThrownBy(this::register).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(COPY));
        assertThat(storedLoans()).isZero();
    }

    @Test
    void shouldLetTheNotFoundOfTheReaderPass() {
        given(readerState.getBorrowingState(READER)).willThrow(new ResourceNotFoundException("leitor não encontrado: 20"));

        assertThatThrownBy(this::register).isInstanceOf(ResourceNotFoundException.class);
        assertThat(storedLoans()).isZero();
    }

    @Test
    void shouldRequireTheCopyAndTheReader() {
        assertThatThrownBy(() -> service.register(new RegisterLoanCommand(null, READER)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.register(new RegisterLoanCommand(COPY, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void ifMarkingTheCopyFailsNoLoanShouldBeSaved() {
        doThrow(new BusinessRuleException("o exemplar só pode ser emprestado se estiver disponível e ativo"))
                .when(checkOutCopy).checkOut(COPY);

        assertThatThrownBy(this::register).isInstanceOf(BusinessRuleException.class);

        assertThat(storedLoans()).isZero();
    }
}
