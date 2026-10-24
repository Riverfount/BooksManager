package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.loan.application.port.in.EndSuspensionCommand;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ending a suspension before its calculated end (RN15, RF20): the service only orchestrates;
 * the justification, and the rule that ending one that is already over changes nothing, are the
 * domain's.
 */
class EndSuspensionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long READER = 20L;

    private InMemorySuspensionRepository suspensionRepository;
    private EndSuspensionService service;

    @BeforeEach
    void setUp() {
        suspensionRepository = new InMemorySuspensionRepository();
        service = new EndSuspensionService(suspensionRepository, CLOCK, new RecordedAudit());
    }

    /** A suspension that started {@code daysAgo} days ago and lasts {@code days} days. */
    private Long suspension(int daysAgo, int days) {
        return suspensionRepository.save(Suspension.create(READER, 1L, TODAY.minusDays(daysAgo), days)).getId();
    }

    private Suspension stored(Long id) {
        return suspensionRepository.findById(id).orElseThrow();
    }

    private boolean readerIsSuspendedToday() {
        return suspensionRepository.findAll(new SuspensionFilter(READER, null, TODAY, null),
                PageQuery.of(0, 1, "id")).totalItems() > 0;
    }

    @Test
    void shouldEndASuspensionInForceFromTodayRecordingTheJustification() {
        var id = suspension(1, 10); // ends in 9 days

        var details = service.end(new EndSuspensionCommand(id, "  Pardoned by the librarian  "));

        assertThat(details.inForce()).isFalse();
        assertThat(details.suspension().getEndedEarlyOn()).isEqualTo(TODAY);
        assertThat(details.suspension().getEndedEarlyReason()).isEqualTo("Pardoned by the librarian");
        assertThat(details.suspension().releaseDate()).isEqualTo(TODAY);
        assertThat(stored(id).getEndedEarlyOn()).isEqualTo(TODAY);
        assertThat(stored(id).getEndDate()).as("the calculated end stays as history").isEqualTo(TODAY.plusDays(9));
    }

    @Test
    void afterwardsTheReaderShouldNoLongerBeSuspended() {
        var id = suspension(1, 10);
        assertThat(readerIsSuspendedToday()).isTrue();

        service.end(new EndSuspensionCommand(id, "Pardoned"));

        assertThat(readerIsSuspendedToday()).isFalse();
    }

    @Test
    void shouldRefuseWithoutAJustificationAndKeepTheSuspensionInForce() {
        var id = suspension(1, 10);

        assertThatThrownBy(() -> service.end(new EndSuspensionCommand(id, "   ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justificativa");
        assertThatThrownBy(() -> service.end(new EndSuspensionCommand(id, null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(stored(id).isEndedEarly()).isFalse();
        assertThat(readerIsSuspendedToday()).isTrue();
    }

    @Test
    void shouldRefuseAJustificationThatIsTooLong() {
        var id = suspension(1, 10);

        assertThatThrownBy(() -> service.end(new EndSuspensionCommand(id, "x".repeat(Suspension.MAX_REASON_LENGTH + 1))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(stored(id).isEndedEarly()).isFalse();
    }

    @Test
    void endingASuspensionThatAlreadyEndedByItselfShouldChangeNothing() {
        var id = suspension(10, 3); // over since 7 days ago

        var details = service.end(new EndSuspensionCommand(id, "Too late"));

        assertThat(details.inForce()).isFalse();
        assertThat(details.suspension().isEndedEarly()).isFalse();
        assertThat(stored(id).isEndedEarly()).isFalse();
        assertThat(stored(id).getEndedEarlyReason()).isNull();
    }

    @Test
    void endingASuspensionThatEndsTodayShouldChangeNothing() {
        var id = suspension(3, 3); // free again today

        service.end(new EndSuspensionCommand(id, "Too late"));

        assertThat(stored(id).isEndedEarly()).isFalse();
    }

    @Test
    void endingASuspensionAlreadyEndedEarlyShouldKeepTheFirstRecord() {
        var id = suspension(1, 10);
        service.end(new EndSuspensionCommand(id, "First reason"));

        var again = service.end(new EndSuspensionCommand(id, "Second reason"));

        assertThat(again.suspension().getEndedEarlyReason()).isEqualTo("First reason");
        assertThat(stored(id).getEndedEarlyReason()).isEqualTo("First reason");
    }

    @Test
    void anEntirelyMalformedRequestIsRefusedEvenWhenThereIsNothingToEnd() {
        var id = suspension(10, 3);

        assertThatThrownBy(() -> service.end(new EndSuspensionCommand(id, " ")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldFailWithNotFoundForASuspensionThatDoesNotExist() {
        assertThatThrownBy(() -> service.end(new EndSuspensionCommand(999L, "Reason")))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("999");
    }

    @Test
    void shouldUseTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 11th is still the 10th in São Paulo
        var lateNight = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZONE);
        var lateService = new EndSuspensionService(suspensionRepository, lateNight, new RecordedAudit());
        var id = suspension(1, 10);

        lateService.end(new EndSuspensionCommand(id, "Pardoned"));

        assertThat(stored(id).getEndedEarlyOn()).isEqualTo(TODAY);
    }
}
