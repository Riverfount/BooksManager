package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.loan.application.port.in.GetOwnStandingQuery;
import com.riverfount.booksmanager.loan.application.port.in.ReaderStanding;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderBorrowingState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How the reader stands (Phase 6): the reasons are the ones the desk would refuse a loan for, without repeating a
 * rule, and everything is the reader's own: the facts of another reader never count.
 */
class OwnStandingServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long ME = 20L;
    private static final long OTHER = 21L;

    private InMemoryLibraryConfigurationRepository configurationRepository;
    private InMemoryLoanRepository loanRepository;
    private InMemoryFineRepository fineRepository;
    private InMemorySuspensionRepository suspensionRepository;
    private InMemoryReservationRepository reservationRepository;
    private GetReaderBorrowingStateUseCase readerState;
    private OwnStandingService service;
    private long nextCopy = 100L;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        loanRepository = new InMemoryLoanRepository();
        fineRepository = new InMemoryFineRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        reservationRepository = new InMemoryReservationRepository();
        readerState = mock(GetReaderBorrowingStateUseCase.class);
        readerIs(ME, true, false);
        readerIs(OTHER, true, false);
        service = new OwnStandingService(configurationRepository, loanRepository, fineRepository,
                suspensionRepository, reservationRepository, readerState, CLOCK);
    }

    private void readerIs(long id, boolean active, boolean blocked) {
        given(readerState.getBorrowingState(id)).willReturn(new ReaderBorrowingState(id, active, blocked,
                active && !blocked));
    }

    private ReaderStanding standing(long reader) {
        return service.get(new GetOwnStandingQuery(reader));
    }

    private Loan openLoan(long reader, int daysAgo) {
        return loanRepository.save(Loan.create(nextCopy++, reader, TODAY.minusDays(daysAgo), 14));
    }

    @Test
    void shouldSayAReaderInGoodStandingCanBorrow() {
        openLoan(ME, 5);
        reservationRepository.save(Reservation.create(30L, ME, TODAY.minusDays(1)));

        var standing = standing(ME);

        assertThat(standing.canBorrow()).isTrue();
        assertThat(standing.blocked()).isFalse();
        assertThat(standing.reasons()).isEmpty();
        assertThat(standing.pendingFineTotal()).isEqualByComparingTo("0");
        assertThat(standing.suspension()).isNull();
        assertThat(standing.activeLoans()).isEqualTo(1);
        assertThat(standing.overdueLoans()).isZero();
        assertThat(standing.activeReservations()).isEqualTo(1);
        assertThat(standing.loanLimit()).isEqualTo(configurationRepository.get().getLoanLimit());
        assertThat(standing.reservationLimit()).isEqualTo(standing.loanLimit());
    }

    @Test
    void shouldTakeTheLimitsFromTheConfiguration() {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, null, 5, null, null, null);
        configurationRepository.save(configuration);

        var standing = standing(ME);

        assertThat(standing.loanLimit()).isEqualTo(5);
        assertThat(standing.reservationLimit()).isEqualTo(5);
    }

    @Test
    void shouldShowABlockedReaderAsBlockedWithoutAnyReasonOfTheBlock() {
        readerIs(ME, true, true);

        var standing = standing(ME);

        assertThat(standing.canBorrow()).isFalse();
        assertThat(standing.blocked()).isTrue();
        assertThat(standing.reasons()).containsExactly("o leitor está bloqueado");
    }

    @Test
    void shouldShowAnInactiveReader() {
        readerIs(ME, false, false);

        assertThat(standing(ME).reasons()).containsExactly("o leitor está inativo");
    }

    @Test
    void shouldTotalThePendingFinesAndIgnoreThePaidOnes() {
        fineRepository.save(Fine.create(1L, ME, new BigDecimal("3.50")));
        fineRepository.save(Fine.create(2L, ME, new BigDecimal("6.25")));
        var paid = fineRepository.save(Fine.create(3L, ME, new BigDecimal("4.00")));
        paid.pay();
        fineRepository.save(paid);

        var standing = standing(ME);

        assertThat(standing.canBorrow()).isFalse();
        assertThat(standing.pendingFineTotal()).isEqualByComparingTo("9.75");
        assertThat(standing.reasons()).containsExactly("o leitor tem multa pendente");
    }

    @Test
    void shouldShowTheSuspensionInForceWithItsReleaseDate() {
        var suspension = suspensionRepository.save(Suspension.create(ME, 1L, TODAY.minusDays(1), 4));

        var standing = standing(ME);

        assertThat(standing.canBorrow()).isFalse();
        assertThat(standing.reasons()).containsExactly("o leitor está suspenso");
        assertThat(standing.suspension().startDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(standing.suspension().releaseDate()).isEqualTo(suspension.releaseDate());
        assertThat(suspension.releaseDate()).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    void shouldNotShowASuspensionThatAlreadyEndedOrWasEndedEarly() {
        suspensionRepository.save(Suspension.create(ME, 1L, TODAY.minusDays(10), 3));
        var early = suspensionRepository.save(Suspension.create(ME, 2L, TODAY.minusDays(1), 10));
        early.endEarly(TODAY, "ended by the desk");
        suspensionRepository.save(early);

        var standing = standing(ME);

        assertThat(standing.suspension()).isNull();
        assertThat(standing.canBorrow()).isTrue();
    }

    @Test
    void shouldCountTheLoansAndTheLateOnes() {
        openLoan(ME, 5);
        openLoan(ME, 30); // due 16 days ago: late

        var standing = standing(ME);

        assertThat(standing.activeLoans()).isEqualTo(2);
        assertThat(standing.overdueLoans()).isEqualTo(1);
        assertThat(standing.canBorrow()).isFalse();
        assertThat(standing.reasons()).containsExactly("o leitor tem empréstimo em atraso");
    }

    @Test
    void shouldReportTheLimitOfLoansLikeTheDeskDoes() {
        for (var i = 0; i < configurationRepository.get().getLoanLimit(); i++) {
            openLoan(ME, 2);
        }

        assertThat(standing(ME).reasons()).singleElement().asString().startsWith("o leitor já atingiu o limite de");
    }

    @Test
    void shouldReportEveryReasonAtOnceInTheOrderOfTheDesk() {
        readerIs(ME, true, true);
        fineRepository.save(Fine.create(1L, ME, new BigDecimal("3.00")));
        suspensionRepository.save(Suspension.create(ME, 1L, TODAY.minusDays(1), 4));
        openLoan(ME, 30);

        assertThat(standing(ME).reasons()).containsExactly("o leitor está bloqueado", "o leitor tem multa pendente",
                "o leitor está suspenso", "o leitor tem empréstimo em atraso");
    }

    @Test
    void shouldNeverCountTheFactsOfAnotherReader() {
        readerIs(OTHER, true, true);
        fineRepository.save(Fine.create(1L, OTHER, new BigDecimal("3.00")));
        suspensionRepository.save(Suspension.create(OTHER, 1L, TODAY.minusDays(1), 4));
        openLoan(OTHER, 30);
        reservationRepository.save(Reservation.create(30L, OTHER, TODAY.minusDays(1)));

        var standing = standing(ME);

        assertThat(standing.canBorrow()).isTrue();
        assertThat(standing.blocked()).isFalse();
        assertThat(standing.pendingFineTotal()).isEqualByComparingTo("0");
        assertThat(standing.suspension()).isNull();
        assertThat(standing.activeLoans()).isZero();
        assertThat(standing.activeReservations()).isZero();
    }
}
