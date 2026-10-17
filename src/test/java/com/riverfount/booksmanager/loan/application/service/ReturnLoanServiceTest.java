package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.catalog.application.port.in.CheckInCopyUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private ReturnLoanService service;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        loanRepository = new InMemoryLoanRepository();
        fineRepository = new InMemoryFineRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        checkInCopy = mock(CheckInCopyUseCase.class);
        service = new ReturnLoanService(configurationRepository, loanRepository, fineRepository,
                suspensionRepository, checkInCopy, CLOCK);
    }

    private void policyIs(PenaltyPolicy policy) {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(policy, null, null, null, null, null, null, null, null);
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

        then(checkInCopy).should().checkIn(COPY);
    }

    @Test
    void shouldUseTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 11th is still the 10th in São Paulo
        var lateNight = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZONE);
        var lateService = new ReturnLoanService(configurationRepository, loanRepository, fineRepository,
                suspensionRepository, checkInCopy, lateNight);

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
                null, null, null);
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
        assertThat(suspensionRepository.findAll(new SuspensionFilter(READER, null, null), ALL).totalItems())
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

        then(checkInCopy).should(org.mockito.Mockito.times(1)).checkIn(COPY);
    }

    @Test
    void shouldFailWithNotFoundForALoanThatDoesNotExist() {
        assertThatThrownBy(() -> service.returnLoan(returning(999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
        then(checkInCopy).should(never()).checkIn(anyLong());
    }

    @Test
    void ifMarkingTheCopyFailsNothingShouldBeSaved() {
        policyIs(PenaltyPolicy.BOTH);
        var id = lateLoan();
        doThrow(new BusinessRuleException("o exemplar só pode ser devolvido se estiver emprestado"))
                .when(checkInCopy).checkIn(COPY);

        assertThatThrownBy(() -> service.returnLoan(returning(id))).isInstanceOf(BusinessRuleException.class);

        assertThat(loanRepository.findById(id).orElseThrow().isActive()).isTrue();
        assertThat(fines()).isZero();
        assertThat(suspensions()).isZero();
    }
}
