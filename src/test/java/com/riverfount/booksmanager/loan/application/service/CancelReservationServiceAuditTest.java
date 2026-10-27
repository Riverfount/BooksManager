package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.catalog.application.port.in.CopyAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReleaseCopyReservationUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReserveCopyUseCase;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationCommand;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
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
 * What cancelling a reservation reports to the audit trail (RF27, RF17): one action for the desk and for the reader,
 * since the author already tells them apart, and a summary with no personal data and no asset code.
 */
class CancelReservationServiceAuditTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long BOOK = 30L;
    private static final long COPY = 7L;

    private InMemoryReservationRepository reservationRepository;
    private RecordedAudit audit;
    private CancelReservationService service;

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        audit = new RecordedAudit();
        var copyAvailability = mock(FindCopyAvailabilityUseCase.class);
        var copySummaries = mock(FindCopySummariesUseCase.class);
        given(copyAvailability.findAvailability(COPY))
                .willReturn(Optional.of(new CopyAvailability(COPY, true, false, true)));
        given(copySummaries.findSummaries(Set.of(COPY)))
                .willReturn(Map.of(COPY, new CopySummary(COPY, "PAT-7", BOOK, "T")));
        var queue = new ReservationQueue(reservationRepository, copyAvailability, copySummaries,
                mock(ReserveCopyUseCase.class), mock(FindReaderContactsUseCase.class), mock(NotificationSender.class));
        service = new CancelReservationService(new InMemoryLibraryConfigurationRepository(), reservationRepository,
                mock(ReleaseCopyReservationUseCase.class), queue, CLOCK, audit);
    }

    @Test
    void shouldReportTheCancellationOfAReservationThatWaitsAsDoneByTheCaller() {
        var reservation = reservationRepository.save(Reservation.create(BOOK, 1L, TODAY.minusDays(3)));

        service.cancel(new CancelReservationCommand(reservation.getId()));

        assertThat(audit.all()).hasSize(1);
        var recorded = audit.all().getFirst();
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.RESERVATION_CANCELLED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.RESERVATION);
        assertThat(recorded.event().targetId()).isEqualTo(reservation.getId());
        assertThat(recorded.event().summary()).isEqualTo("livro 30, na fila");
    }

    @Test
    void shouldSayInTheSummaryThatTheReservationHeldACopyWithoutNamingIt() {
        var reservation = Reservation.create(BOOK, 1L, TODAY.minusDays(5));
        reservation.assignCopy(COPY, TODAY.minusDays(1), 3);
        var saved = reservationRepository.save(reservation);

        service.cancel(new CancelReservationCommand(saved.getId()));

        var summary = audit.all().getFirst().event().summary();
        assertThat(summary).isEqualTo("livro 30, com exemplar reservado").doesNotContain("PAT-7");
    }

    @Test
    void shouldReportNothingWhenTheCancellationIsRefused() {
        var reservation = reservationRepository.save(Reservation.create(BOOK, 1L, TODAY.minusDays(3)));
        service.cancel(new CancelReservationCommand(reservation.getId()));
        var before = audit.all().size();

        assertThatThrownBy(() -> service.cancel(new CancelReservationCommand(reservation.getId())))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.cancel(new CancelReservationCommand(999L)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(audit.all()).hasSize(before);
    }
}
