package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.loan.application.port.in.ListFinesQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListSuspensionsQuery;
import com.riverfount.booksmanager.loan.application.port.in.SuspensionDetails;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Queries on fines and suspensions (RF22, HU11, RNF14): the situation of a reader to decide a
 * new loan. "In force" is the domain's own definition, asked for the day of the clock.
 */
class FineAndSuspensionQueryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long READER = 20L;
    private static final long OTHER_READER = 21L;

    private InMemoryFineRepository fineRepository;
    private InMemorySuspensionRepository suspensionRepository;
    private FineQueryService fineService;
    private SuspensionQueryService suspensionService;

    @BeforeEach
    void setUp() {
        fineRepository = new InMemoryFineRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        fineService = new FineQueryService(fineRepository);
        suspensionService = new SuspensionQueryService(suspensionRepository, CLOCK);
    }

    private Fine fine(long loan, long reader, String amount) {
        return fineRepository.save(Fine.create(loan, reader, new BigDecimal(amount)));
    }

    private Suspension suspension(long loan, long reader, LocalDate start, int days) {
        return suspensionRepository.save(Suspension.create(reader, loan, start, days));
    }

    private static ListFinesQuery fines(Long reader, FineStatus status) {
        return new ListFinesQuery(reader, status, PageQuery.of(0, 20, "id"));
    }

    private static ListSuspensionsQuery suspensions(Long reader, Boolean inForce) {
        return new ListSuspensionsQuery(reader, inForce, PageQuery.of(0, 20, "id"));
    }

    // fines

    @Test
    void shouldListTheFinesOfAReader() {
        var mine = fine(1L, READER, "3.00");
        fine(2L, OTHER_READER, "4.00");

        var page = fineService.list(fines(READER, null));

        assertThat(page.items()).extracting(Fine::getId).containsExactly(mine.getId());
    }

    @Test
    void shouldFilterTheFinesByStatus() {
        var pending = fine(1L, READER, "3.00");
        var paid = fine(2L, READER, "4.00");
        paid.pay();
        fineRepository.save(paid);
        var waived = fine(3L, READER, "5.00");
        waived.waive("Reason");
        fineRepository.save(waived);

        assertThat(fineService.list(fines(READER, FineStatus.PENDING)).items())
                .extracting(Fine::getId).containsExactly(pending.getId());
        assertThat(fineService.list(fines(READER, FineStatus.PAID)).items())
                .extracting(Fine::getId).containsExactly(paid.getId());
        assertThat(fineService.list(fines(READER, FineStatus.WAIVED)).items())
                .extracting(Fine::getId).containsExactly(waived.getId());
        assertThat(fineService.list(fines(READER, null)).items()).hasSize(3);
    }

    @Test
    void shouldPaginateAndSortTheFines() {
        fine(1L, READER, "3.00");
        fine(2L, READER, "1.00");
        fine(3L, READER, "2.00");

        var page = fineService.list(new ListFinesQuery(READER, null, PageQuery.of(0, 2, "amount,desc")));

        assertThat(page.items()).extracting(Fine::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.00"), new BigDecimal("2.00"));
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void shouldRefuseASortFieldOfFinesThatIsNotSupported() {
        assertThatThrownBy(() -> new ListFinesQuery(null, null, PageQuery.of(0, 20, "readerId")))
                .isInstanceOf(InvalidQueryException.class).hasMessageContaining("readerId");
    }

    @Test
    void theDefaultSortOfFinesShouldBeValid() {
        assertThat(new ListFinesQuery(null, null, PageQuery.of(0, 20, ListFinesQuery.DEFAULT_SORT))).isNotNull();
        for (var field : ListFinesQuery.SORTABLE_FIELDS) {
            assertThat(new ListFinesQuery(null, null, PageQuery.of(0, 20, field))).isNotNull();
        }
    }

    // suspensions: "in force" (acceptance: one that ended does not appear as in force)

    @Test
    void aSuspensionThatAlreadyEndedShouldNotAppearAsInForce() {
        var ended = suspension(1L, READER, TODAY.minusDays(10), 3);   // over since 7 days ago
        var running = suspension(2L, READER, TODAY.minusDays(1), 5);  // until 4 days from now

        var inForce = suspensionService.list(suspensions(READER, true));

        assertThat(inForce.items()).extracting(details -> details.suspension().getId())
                .containsExactly(running.getId());
        assertThat(inForce.items()).allMatch(SuspensionDetails::inForce);
        assertThat(ended.getId()).isNotNull();
    }

    @Test
    void theLastDayShouldStillBeInForceAndTheEndDateShouldNot() {
        var lastDay = suspension(1L, READER, TODAY.minusDays(2), 3);   // free again today + 1
        var endsToday = suspension(2L, READER, TODAY.minusDays(3), 3); // free again today

        var inForce = suspensionService.list(suspensions(READER, true));

        assertThat(inForce.items()).extracting(details -> details.suspension().getId())
                .containsExactly(lastDay.getId());
        assertThat(endsToday.releaseDate()).isEqualTo(TODAY);
    }

    @Test
    void aSuspensionEndedEarlyShouldNotBeInForce() {
        var early = suspension(1L, READER, TODAY.minusDays(1), 30);
        early.endEarly(TODAY, "Pardoned");
        suspensionRepository.save(early);

        assertThat(suspensionService.list(suspensions(READER, true)).items()).isEmpty();
        var notInForce = suspensionService.list(suspensions(READER, false));
        assertThat(notInForce.items()).hasSize(1);
        assertThat(notInForce.items().get(0).inForce()).isFalse();
    }

    @Test
    void aSuspensionThatDoesNotStartYetShouldNotBeInForce() {
        suspension(1L, READER, TODAY.plusDays(2), 3);

        assertThat(suspensionService.list(suspensions(READER, true)).items()).isEmpty();
        assertThat(suspensionService.list(suspensions(READER, false)).items()).hasSize(1);
    }

    @Test
    void withoutTheFilterAllShouldComeEachSayingIfItIsInForce() {
        var ended = suspension(1L, READER, TODAY.minusDays(10), 3);
        var running = suspension(2L, READER, TODAY.minusDays(1), 5);

        var page = suspensionService.list(suspensions(READER, null));

        assertThat(page.items()).extracting(details -> details.suspension().getId())
                .containsExactly(ended.getId(), running.getId());
        assertThat(page.items()).extracting(SuspensionDetails::inForce).containsExactly(false, true);
    }

    @Test
    void shouldListOnlyTheSuspensionsOfTheReader() {
        suspension(1L, OTHER_READER, TODAY.minusDays(1), 5);
        var mine = suspension(2L, READER, TODAY.minusDays(1), 5);

        var page = suspensionService.list(suspensions(READER, null));

        assertThat(page.items()).extracting(details -> details.suspension().getId()).containsExactly(mine.getId());
    }

    @Test
    void shouldUseTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 11th is still the 10th in São Paulo: a suspension free on the 11th is in force
        var lateNight = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZONE);
        var lateService = new SuspensionQueryService(suspensionRepository, lateNight);
        suspension(1L, READER, TODAY.minusDays(1), 2);

        assertThat(lateService.list(suspensions(READER, true)).items()).hasSize(1);
    }

    @Test
    void shouldPaginateAndSortTheSuspensions() {
        for (var days = 1; days <= 3; days++) {
            suspension(days, READER, TODAY.minusDays(1), days + 1);
        }

        var page = suspensionService.list(
                new ListSuspensionsQuery(READER, null, PageQuery.of(0, 2, "endDate,desc")));

        assertThat(page.items()).extracting(details -> details.suspension().getEndDate())
                .containsExactly(TODAY.minusDays(1).plusDays(4), TODAY.minusDays(1).plusDays(3));
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void shouldRefuseASortFieldOfSuspensionsThatIsNotSupported() {
        assertThatThrownBy(() -> new ListSuspensionsQuery(null, null, PageQuery.of(0, 20, "readerId")))
                .isInstanceOf(InvalidQueryException.class).hasMessageContaining("readerId");
    }

    @Test
    void theDefaultSortOfSuspensionsShouldBeValid() {
        assertThat(new ListSuspensionsQuery(null, null, PageQuery.of(0, 20, ListSuspensionsQuery.DEFAULT_SORT)))
                .isNotNull();
        for (var field : ListSuspensionsQuery.SORTABLE_FIELDS) {
            assertThat(new ListSuspensionsQuery(null, null, PageQuery.of(0, 20, field))).isNotNull();
        }
    }
}
