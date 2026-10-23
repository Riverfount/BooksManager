package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.catalog.application.port.in.CheckInCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.CopyAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReserveCopyUseCase;
import com.riverfount.booksmanager.catalog.domain.CopyReturnCondition;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReturnCondition;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Returning a loan (RF12, RF20, HU08): the service only orchestrates; the lateness, the
 * penalty and the closing are the domain's (RN08, RN09, RN16). Each variant of the
 * penalty policy is exercised on its own.
 */
class ReturnLoanServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long COPY = 7L;
    private static final long READER = 20L;
    private static final PageQuery ALL = PageQuery.of(0, 100, "id");

    private InMemoryLibraryConfigurationRepository configurationRepository;
    private InMemoryLoanRepository loanRepository;
    private InMemoryFineRepository fineRepository;
    private InMemorySuspensionRepository suspensionRepository;
    private CheckInCopyUseCase checkInCopy;
    private InMemoryReservationRepository reservationRepository;
    private FindCopyAvailabilityUseCase copyAvailability;
    private FindCopySummariesUseCase copySummaries;
    private ReserveCopyUseCase reserveCopy;
    private ReturnLoanService service;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        loanRepository = new InMemoryLoanRepository();
        fineRepository = new InMemoryFineRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        checkInCopy = mock(CheckInCopyUseCase.class);
        reservationRepository = new InMemoryReservationRepository();
        copyAvailability = mock(FindCopyAvailabilityUseCase.class);
        copySummaries = mock(FindCopySummariesUseCase.class);
        reserveCopy = mock(ReserveCopyUseCase.class);
        var reservationQueue = new ReservationQueue(reservationRepository, copyAvailability, copySummaries,
                reserveCopy, mock(FindReaderContactsUseCase.class), mock(NotificationSender.class));
        service = new ReturnLoanService(configurationRepository, loanRepository, fineRepository,
                suspensionRepository, checkInCopy, reservationQueue, CLOCK);
    }

    private void policyIs(PenaltyPolicy policy) {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(policy, null, null, null, null, null, null, null, null, null);
        configurationRepository.save(configuration);
    }

    /** A loan picked up {@code daysAgo} days ago for 14 days, so it is {@code daysAgo - 14} days late. */
    private Long openLoan(int daysAgo) {
        return loanRepository.save(Loan.create(COPY, READER, TODAY.minusDays(daysAgo), 14)).getId();
    }

    private Long lateLoan() {
        return openLoan(20); // due 6 days ago
    }

    private ReturnLoanCommand returning(Long loanId) {
        return new ReturnLoanCommand(loanId);
    }

    private long fines() {
        return fineRepository.findAll(FineFilter.none(), ALL).totalItems();
    }

    private long suspensions() {
        return suspensionRepository.findAll(SuspensionFilter.none(), ALL).totalItems();
    }

    // closing the loan (RF12)

    @Test
    void shouldCloseTheLoanWithTodayAsTheReturnDate() {
        var id = openLoan(5);

        var result = service.returnLoan(returning(id));

        assertThat(result.loan().getReturnDate()).isEqualTo(TODAY);
        assertThat(result.loan().isReturned()).isTrue();
        assertThat(loanRepository.findById(id).orElseThrow().getReturnDate()).isEqualTo(TODAY);
    }

    @Test
    void shouldMarkTheCopyAsAvailableAgain() {
        service.returnLoan(returning(openLoan(5)));

        then(checkInCopy).should().checkIn(COPY, CopyReturnCondition.GOOD);
    }

    // the queue of reservations (RF18, RN13)

    private static final long BOOK = 30L;

    private void copyIsBackOnTheShelfOfBook(long book) {
        given(copyAvailability.findAvailability(COPY)).willReturn(Optional.of(new CopyAvailability(COPY, true, false)));
        given(copySummaries.findSummaries(Set.of(COPY)))
                .willReturn(Map.of(COPY, new CopySummary(COPY, "PAT-7", book, "T")));
    }

    private Reservation waitingFor(long book, long reader) {
        return reservationRepository.save(Reservation.create(book, reader, TODAY.minusDays(1)));
    }

    @Test
    void shouldGiveTheReturnedCopyToTheFirstOfTheQueueWithTheConfiguredDeadline() {
        copyIsBackOnTheShelfOfBook(BOOK);
        var first = waitingFor(BOOK, 21L);
        var second = waitingFor(BOOK, 22L);

        var result = service.returnLoan(returning(openLoan(5)));

        assertThat(result.reservation().getId()).isEqualTo(first.getId());
        assertThat(result.reservation().getCopyId()).isEqualTo(COPY);
        assertThat(result.reservation().getPickupDeadline()).isEqualTo(TODAY.plusDays(3));
        then(reserveCopy).should().reserve(COPY);
        assertThat(reservationRepository.queuePosition(second.getId())).contains(1L);
    }

    @Test
    void shouldUseThePickupDaysOfTheConfigurationAtTheMoment() {
        copyIsBackOnTheShelfOfBook(BOOK);
        waitingFor(BOOK, 21L);
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, null, null, null, 5, null);
        configurationRepository.save(configuration);

        var result = service.returnLoan(returning(openLoan(5)));

        assertThat(result.reservation().getPickupDeadline()).isEqualTo(TODAY.plusDays(5));
    }

    @Test
    void shouldLeaveTheCopyAvailableWhenNobodyWaitsForTheBook() {
        copyIsBackOnTheShelfOfBook(BOOK);
        waitingFor(31L, 21L);

        var result = service.returnLoan(returning(openLoan(5)));

        assertThat(result.reservation()).isNull();
        then(reserveCopy).should(never()).reserve(anyLong());
    }

    @ParameterizedTest
    @EnumSource(value = ReturnCondition.class, names = {"DAMAGED", "LOST"})
    void aDamagedOrLostCopyShouldNeverEnterTheQueue(ReturnCondition condition) {
        copyIsBackOnTheShelfOfBook(BOOK);
        var waiting = waitingFor(BOOK, 21L);

        var result = service.returnLoan(new ReturnLoanCommand(openLoan(5), condition));

        assertThat(result.reservation()).isNull();
        then(reserveCopy).should(never()).reserve(anyLong());
        assertThat(reservationRepository.findById(waiting.getId()).orElseThrow().isWaiting()).isTrue();
    }

    @Test
    void shouldFailTheWholeReturnWhenTheCopyCannotBeKeptForTheQueue() {
        copyIsBackOnTheShelfOfBook(BOOK);
        waitingFor(BOOK, 21L);
        doThrow(new BusinessRuleException("o exemplar não está disponível")).when(reserveCopy).reserve(COPY);

        assertThatThrownBy(() -> service.returnLoan(returning(openLoan(5))))
                .isInstanceOf(BusinessRuleException.class);
    }

    // the condition of the copy (RF15)

    @ParameterizedTest
    @EnumSource(ReturnCondition.class)
    void shouldKeepTheConditionInTheLoanAndTellTheCatalogInItsOwnType(ReturnCondition condition) {
        var id = openLoan(5);

        var result = service.returnLoan(new ReturnLoanCommand(id, condition));

        assertThat(result.loan().getReturnCondition()).isEqualTo(condition);
        assertThat(loanRepository.findById(id).orElseThrow().getReturnCondition()).isEqualTo(condition);
        then(checkInCopy).should().checkIn(COPY, CopyReturnCondition.valueOf(condition.name()));
    }

    @Test
    void aReturnWithoutConditionShouldCountAsGood() {
        var result = service.returnLoan(new ReturnLoanCommand(openLoan(5), null));

        assertThat(result.loan().getReturnCondition()).isEqualTo(ReturnCondition.GOOD);
        then(checkInCopy).should().checkIn(COPY, CopyReturnCondition.GOOD);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnCondition.class, names = {"DAMAGED", "LOST"})
    void aLateReturnShouldBePenalizedAlsoWhenTheCopyIsDamagedOrLost(ReturnCondition condition) {
        policyIs(PenaltyPolicy.BOTH);

        var result = service.returnLoan(new ReturnLoanCommand(lateLoan(), condition));

        assertThat(result.daysLate()).isEqualTo(6);
        assertThat(result.fine()).isNotNull();
        assertThat(result.suspension()).isNotNull();
        assertThat(fines()).isEqualTo(1);
        assertThat(suspensions()).isEqualTo(1);
    }

    @Test
    void aDamagedReturnOnTimeShouldApplyNoPenalty() {
        policyIs(PenaltyPolicy.BOTH);

        var result = service.returnLoan(new ReturnLoanCommand(openLoan(5), ReturnCondition.DAMAGED));

        assertThat(result.fine()).isNull();
        assertThat(result.suspension()).isNull();
    }

    @Test
    void ifTheCatalogRefusesTheConditionNothingShouldBeSaved() {
        var id = openLoan(5);
        doThrow(new BusinessRuleException("o exemplar só pode ser devolvido se estiver emprestado"))
                .when(checkInCopy).checkIn(COPY, CopyReturnCondition.LOST);

        assertThatThrownBy(() -> service.returnLoan(new ReturnLoanCommand(id, ReturnCondition.LOST)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(loanRepository.findById(id).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldUseTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 11th is still the 10th in São Paulo
        var lateNight = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZONE);
        var lateService = new ReturnLoanService(configurationRepository, loanRepository, fineRepository,
                suspensionRepository, checkInCopy,
                new ReservationQueue(reservationRepository, copyAvailability, copySummaries, reserveCopy, mock(FindReaderContactsUseCase.class), mock(NotificationSender.class)), lateNight);

        assertThat(lateService.returnLoan(returning(openLoan(5))).loan().getReturnDate()).isEqualTo(TODAY);
    }

    // on time or early: nothing is applied (HU08)

    @Test
    void aReturnOnTheDueDateShouldApplyNothing() {
        policyIs(PenaltyPolicy.BOTH);

        var result = service.returnLoan(returning(openLoan(14)));

        assertThat(result.daysLate()).isZero();
        assertThat(result.fine()).isNull();
        assertThat(result.suspension()).isNull();
        assertThat(fines()).isZero();
        assertThat(suspensions()).isZero();
    }

    @Test
    void anEarlyReturnShouldApplyNothing() {
        policyIs(PenaltyPolicy.BOTH);

        var result = service.returnLoan(returning(openLoan(2)));

        assertThat(result.daysLate()).isZero();
        assertThat(result.fine()).isNull();
        assertThat(result.suspension()).isNull();
    }

    // one variant of the policy at a time (RN08, RN16)

    @Test
    void theFinePolicyShouldApplyOnlyAFine() {
        policyIs(PenaltyPolicy.FINE);
        var id = lateLoan();

        var result = service.returnLoan(returning(id));

        assertThat(result.daysLate()).isEqualTo(6);
        assertThat(result.fine()).isNotNull();
        assertThat(result.fine().getAmount()).isEqualByComparingTo("6.00");
        assertThat(result.fine().getStatus()).isEqualTo(FineStatus.PENDING);
        assertThat(result.fine().getLoanId()).isEqualTo(id);
        assertThat(result.fine().getReaderId()).isEqualTo(READER);
        assertThat(result.suspension()).isNull();
        assertThat(fines()).isEqualTo(1);
        assertThat(suspensions()).isZero();
    }

    @Test
    void theSuspensionPolicyShouldApplyOnlyASuspensionStartingOnTheReturnDay() {
        policyIs(PenaltyPolicy.SUSPENSION);
        var id = lateLoan();

        var result = service.returnLoan(returning(id));

        assertThat(result.fine()).isNull();
        assertThat(result.suspension()).isNotNull();
        assertThat(result.suspension().getStartDate()).isEqualTo(TODAY);
        assertThat(result.suspension().getEndDate()).isEqualTo(TODAY.plusDays(6));
        assertThat(result.suspension().getLoanId()).isEqualTo(id);
        assertThat(result.suspension().getReaderId()).isEqualTo(READER);
        assertThat(fines()).isZero();
        assertThat(suspensions()).isEqualTo(1);
    }

    @Test
    void theBothPolicyShouldApplyTheFineAndTheSuspension() {
        policyIs(PenaltyPolicy.BOTH);

        var result = service.returnLoan(returning(lateLoan()));

        assertThat(result.fine().getAmount()).isEqualByComparingTo("6.00");
        assertThat(result.suspension().getEndDate()).isEqualTo(TODAY.plusDays(6));
        assertThat(fines()).isEqualTo(1);
        assertThat(suspensions()).isEqualTo(1);
    }

    @Test
    void theNonePolicyShouldApplyNothingEvenWhenLate() {
        policyIs(PenaltyPolicy.NONE);

        var result = service.returnLoan(returning(lateLoan()));

        assertThat(result.daysLate()).isEqualTo(6);
        assertThat(result.fine()).isNull();
        assertThat(result.suspension()).isNull();
        assertThat(result.loan().isReturned()).isTrue();
        assertThat(fines()).isZero();
        assertThat(suspensions()).isZero();
    }

    @Test
    void theNumbersShouldComeFromTheConfigurationAndRespectTheCaps() {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(PenaltyPolicy.BOTH, new BigDecimal("2.50"), new BigDecimal("12.00"), 2, 9, null,
                null, null, null, null);
        configurationRepository.save(configuration);

        var result = service.returnLoan(returning(lateLoan())); // 6 days late

        assertThat(result.fine().getAmount()).as("6 x 2.50 = 15.00, capped at 12.00").isEqualByComparingTo("12.00");
        assertThat(result.suspension().getEndDate()).as("6 x 2 = 12 days, capped at 9")
                .isEqualTo(TODAY.plusDays(9));
    }

    // a change of policy only reaches the next returns (RN16)

    @Test
    void changingThePolicyShouldNotRecalculateWhatWasAlreadyReturned() {
        policyIs(PenaltyPolicy.FINE);
        var first = service.returnLoan(returning(lateLoan()));
        policyIs(PenaltyPolicy.SUSPENSION);

        var second = service.returnLoan(returning(
                loanRepository.save(Loan.create(8L, 21L, TODAY.minusDays(20), 14)).getId()));

        assertThat(fineRepository.findById(first.fine().getId()).orElseThrow().getAmount())
                .isEqualByComparingTo("6.00");
        assertThat(suspensionRepository.findAll(new SuspensionFilter(READER, null, null, null), ALL).totalItems())
                .as("the first return, under the fine policy, got no suspension").isZero();
        assertThat(second.fine()).isNull();
        assertThat(second.suspension()).isNotNull();
    }

    // a closed loan, things that do not exist and failures (RN09, RNF16)

    @Test
    void shouldRefuseToReturnALoanThatWasAlreadyReturned() {
        policyIs(PenaltyPolicy.BOTH);
        var id = lateLoan();
        service.returnLoan(returning(id));

        assertThatThrownBy(() -> service.returnLoan(returning(id))).isInstanceOf(BusinessRuleException.class);

        assertThat(fines()).as("no second fine").isEqualTo(1);
        assertThat(suspensions()).as("no second suspension").isEqualTo(1);
    }

    @Test
    void aRefusedReturnShouldNotTouchTheCopyAgain() {
        var id = openLoan(5);
        service.returnLoan(returning(id));

        assertThatThrownBy(() -> service.returnLoan(returning(id))).isInstanceOf(BusinessRuleException.class);

        then(checkInCopy).should(org.mockito.Mockito.times(1)).checkIn(COPY, CopyReturnCondition.GOOD);
    }

    @Test
    void shouldFailWithNotFoundForALoanThatDoesNotExist() {
        assertThatThrownBy(() -> service.returnLoan(returning(999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        then(checkInCopy).should(never()).checkIn(anyLong(), any());
    }

    @Test
    void ifMarkingTheCopyFailsNothingShouldBeSaved() {
        policyIs(PenaltyPolicy.BOTH);
        var id = lateLoan();
        doThrow(new BusinessRuleException("o exemplar só pode ser devolvido se estiver emprestado"))
                .when(checkInCopy).checkIn(COPY, CopyReturnCondition.GOOD);

        assertThatThrownBy(() -> service.returnLoan(returning(id))).isInstanceOf(BusinessRuleException.class);

        assertThat(loanRepository.findById(id).orElseThrow().isActive()).isTrue();
        assertThat(fines()).isZero();
        assertThat(suspensions()).isZero();
    }
}
