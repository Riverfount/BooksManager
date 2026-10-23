package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import com.riverfount.booksmanager.loan.domain.LoanNoticeKind;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationDeliveryException;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The loan notices (RF28, RN16): the due-soon notice from the days of notice of the configuration up to the due date,
 * the overdue notice once per band of seven late days, never twice (the sent ones are recorded), never for a returned
 * loan, and a failed sending is not recorded so the next run tries again.
 */
class SendLoanNoticesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DUE = LocalDate.of(2026, 11, 10);
    private static final LocalDate PICKUP = DUE.minusDays(14);

    private InMemoryLoanRepository loanRepository;
    private InMemoryLoanNoticeRepository noticeRepository;
    private InMemoryLibraryConfigurationRepository configurationRepository;
    private RecordingSender mail;
    private SendLoanNoticesService service;
    private LocalDate today;

    /** Records what was sent; can be told to fail, like a mail server that is down. */
    private static final class RecordingSender implements NotificationSender {

        final List<Notification> sent = new ArrayList<>();
        boolean down;

        @Override
        public void send(Notification notification) {
            throw new AssertionError("a routine that must know the result uses sendAndConfirm");
        }

        @Override
        public void sendAndConfirm(Notification notification) {
            if (down) {
                throw new NotificationDeliveryException("mail server down", new RuntimeException());
            }
            sent.add(notification);
        }
    }

    @BeforeEach
    void setUp() {
        loanRepository = new InMemoryLoanRepository();
        noticeRepository = new InMemoryLoanNoticeRepository();
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        mail = new RecordingSender();
        today = DUE.minusDays(5);
        var contacts = mock(FindReaderContactsUseCase.class);
        given(contacts.findContacts(anySet())).willAnswer(call -> {
            Map<Long, ReaderContact> all = Map.of(20L, new ReaderContact("Maria Silva", "maria@example.com"),
                    21L, new ReaderContact("João Souza", "joao@example.com"));
            var ids = call.<java.util.Set<Long>>getArgument(0);
            return all.entrySet().stream().filter(e -> ids.contains(e.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
        var summaries = mock(FindCopySummariesUseCase.class);
        given(summaries.findSummaries(anySet())).willAnswer(call -> {
            var ids = call.<java.util.Set<Long>>getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                    id -> new CopySummary(id, "PAT-" + id, 90L, "Dom Casmurro")));
        });
        var sender = new LoanNoticeSender(loanRepository, noticeRepository, mail);
        service = new SendLoanNoticesService(loanRepository, noticeRepository, configurationRepository, contacts,
                summaries, sender, new MutableClock());
    }

    /** A clock whose day the tests move, always at midday in São Paulo. */
    private final class MutableClock extends Clock {

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return today.atTime(12, 0).atZone(ZONE).toInstant();
        }
    }

    private Loan lend(long copy, long reader) {
        return loanRepository.save(Loan.create(copy, reader, PICKUP, 14));
    }

    private static LoanNotice dueSoon() {
        return new LoanNotice(LoanNoticeKind.DUE_SOON, DUE, 0);
    }

    // the due-soon notice

    @Test
    void shouldSendNothingBeforeTheDaysOfNotice() {
        lend(1L, 20L);
        today = DUE.minusDays(3); // the configuration gives 2 days

        assertThat(service.sendNotices()).isZero();
        assertThat(mail.sent).isEmpty();
    }

    @Test
    void shouldWarnTheReaderFromTheDaysOfNoticeOfTheConfiguration() {
        var loan = lend(1L, 20L);
        today = DUE.minusDays(2);

        assertThat(service.sendNotices()).isEqualTo(1);

        assertThat(mail.sent).hasSize(1);
        var message = mail.sent.get(0);
        assertThat(message.recipient()).isEqualTo("maria@example.com");
        assertThat(message.subject()).isEqualTo("Devolução próxima: \"Dom Casmurro\"");
        assertThat(message.text()).contains("Maria Silva").contains("vence em 2 dias").contains("10/11/2026");
        assertThat(noticeRepository.all()).extracting(sent -> sent.loanId()).containsExactly(loan.getId());
    }

    @Test
    void shouldFollowTheDaysOfNoticeTheLibraryConfigured() {
        lend(1L, 20L);
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, null, null, null, null, 5);
        configurationRepository.save(configuration);
        today = DUE.minusDays(5);

        assertThat(service.sendNotices()).isEqualTo(1);
        assertThat(mail.sent.get(0).text()).contains("vence em 5 dias");
    }

    @Test
    void shouldSayTomorrowAndTodayInTheLastDays() {
        lend(1L, 20L);
        today = DUE.minusDays(1);
        service.sendNotices();
        assertThat(mail.sent.get(0).text()).contains("vence amanhã");
    }

    @Test
    void dueTodayShouldStillBeOnTimeSoItIsTheDueSoonNoticeAndNotTheOverdueOne() {
        lend(1L, 20L);
        today = DUE;

        service.sendNotices();

        assertThat(mail.sent).hasSize(1);
        assertThat(mail.sent.get(0).subject()).startsWith("Devolução próxima");
        assertThat(mail.sent.get(0).text()).contains("vence hoje");
    }

    // the overdue notice

    @Test
    void shouldTellTheReaderTheLoanIsLateOnTheFirstLateDay() {
        lend(1L, 20L);
        today = DUE.plusDays(1);

        service.sendNotices();

        assertThat(mail.sent).hasSize(1);
        assertThat(mail.sent.get(0).subject()).isEqualTo("Empréstimo em atraso: \"Dom Casmurro\"");
        assertThat(mail.sent.get(0).text()).contains("atraso há 1 dia ");
    }

    @Test
    void shouldRepeatTheOverdueNoticeOnlyOncePerBandOfSevenDays() {
        lend(1L, 20L);

        today = DUE.plusDays(1);
        assertThat(service.sendNotices()).isEqualTo(1);
        for (int late = 2; late <= 7; late++) {
            today = DUE.plusDays(late);
            assertThat(service.sendNotices()).as("late day %d", late).isZero();
        }
        today = DUE.plusDays(8);
        assertThat(service.sendNotices()).isEqualTo(1);
        today = DUE.plusDays(9);
        assertThat(service.sendNotices()).isZero();
        today = DUE.plusDays(15);
        assertThat(service.sendNotices()).isEqualTo(1);
        assertThat(mail.sent.get(2).text()).contains("atraso há 15 dias");
    }

    @Test
    void shouldCatchUpAfterADayWithoutRunningInsteadOfSkippingTheBand() {
        lend(1L, 20L);
        today = DUE.plusDays(3); // the first late days were missed

        assertThat(service.sendNotices()).isEqualTo(1);
        assertThat(mail.sent.get(0).text()).contains("atraso há 3 dias");
    }

    // idempotence

    @Test
    void runningTwiceInARowShouldSendNothingTheSecondTime() {
        lend(1L, 20L);
        lend(2L, 21L);
        today = DUE.minusDays(1);

        assertThat(service.sendNotices()).isEqualTo(2);
        assertThat(service.sendNotices()).isZero();

        assertThat(mail.sent).hasSize(2);
    }

    @Test
    void shouldSendTheDueSoonNoticeAndLaterTheOverdueOneForTheSameLoan() {
        lend(1L, 20L);
        today = DUE.minusDays(1);
        service.sendNotices();
        today = DUE.plusDays(1);
        service.sendNotices();

        assertThat(mail.sent).extracting(Notification::subject)
                .containsExactly("Devolução próxima: \"Dom Casmurro\"", "Empréstimo em atraso: \"Dom Casmurro\"");
    }

    @Test
    void aRenewalShouldMakeTheNextDueSoonNoticeANewOne() {
        var loan = lend(1L, 20L);
        today = DUE.minusDays(1);
        service.sendNotices();

        var renewed = loanRepository.findById(loan.getId()).orElseThrow();
        renewed.renew(today, 2, 14, false);
        loanRepository.save(renewed);

        assertThat(service.sendNotices()).isZero(); // the new due date is far
        today = DUE.plusDays(13);
        assertThat(service.sendNotices()).isEqualTo(1);
        assertThat(mail.sent.get(1).text()).contains("24/11/2026");
    }

    // who gets nothing

    @Test
    void aReturnedLoanShouldGetNoNotice() {
        var loan = lend(1L, 20L);
        var returned = loanRepository.findById(loan.getId()).orElseThrow();
        returned.returnCopy(DUE.minusDays(3));
        loanRepository.save(returned);
        today = DUE.plusDays(2);

        assertThat(service.sendNotices()).isZero();
        assertThat(mail.sent).isEmpty();
    }

    @Test
    void shouldSkipTheLoanWhoseReaderIsNotFoundAndNotifyTheOthers() {
        lend(1L, 99L);
        lend(2L, 20L);
        today = DUE.minusDays(1);

        assertThat(service.sendNotices()).isEqualTo(1);
        assertThat(mail.sent).extracting(Notification::recipient).containsExactly("maria@example.com");
    }

    @Test
    void shouldFallBackToAGenericTitleWhenTheCopyIsNotFound() {
        var summaries = mock(FindCopySummariesUseCase.class);
        given(summaries.findSummaries(anySet())).willReturn(Map.of());
        var contacts = mock(FindReaderContactsUseCase.class);
        given(contacts.findContacts(anySet()))
                .willReturn(Map.of(20L, new ReaderContact("Maria Silva", "maria@example.com")));
        service = new SendLoanNoticesService(loanRepository, noticeRepository, configurationRepository, contacts,
                summaries, new LoanNoticeSender(loanRepository, noticeRepository, mail), new MutableClock());
        lend(1L, 20L);
        today = DUE.minusDays(1);

        service.sendNotices();

        assertThat(mail.sent.get(0).subject()).isEqualTo("Devolução próxima: um livro da biblioteca");
    }

    // failure

    /**
     * That the failed notice is not kept as sent rests on the transaction rolling the record back, which an in-memory
     * repository cannot show: {@code LoanNoticeIntegrationTest} proves it with a real commit and rollback.
     */
    @Test
    void aFailedSendingShouldBeLoggedAndSentNothingInsteadOfBreakingTheRun() {
        lend(1L, 20L);
        today = DUE.minusDays(1);
        mail.down = true;

        assertThat(service.sendNotices()).isZero();
        assertThat(mail.sent).isEmpty();
    }

    @Test
    void aLoanThatFailsShouldNotHoldBackTheOthers() {
        lend(1L, 20L);
        lend(2L, 21L);
        today = DUE.minusDays(1);
        var failingFirst = new NotificationSender() {
            int calls;

            @Override
            public void send(Notification notification) {
                throw new AssertionError();
            }

            @Override
            public void sendAndConfirm(Notification notification) {
                if (calls++ == 0) {
                    throw new NotificationDeliveryException("down", new RuntimeException());
                }
                mail.sent.add(notification);
            }
        };
        var contacts = mock(FindReaderContactsUseCase.class);
        given(contacts.findContacts(anySet())).willReturn(Map.of(20L, new ReaderContact("Maria", "maria@example.com"),
                21L, new ReaderContact("João", "joao@example.com")));
        var summaries = mock(FindCopySummariesUseCase.class);
        given(summaries.findSummaries(anySet())).willReturn(Map.of());
        service = new SendLoanNoticesService(loanRepository, noticeRepository, configurationRepository, contacts,
                summaries, new LoanNoticeSender(loanRepository, noticeRepository, failingFirst), new MutableClock());

        assertThat(service.sendNotices()).isEqualTo(1);

        assertThat(mail.sent).extracting(Notification::recipient).containsExactly("joao@example.com");
    }

    @Test
    void aLoanReturnedBetweenTheReadingAndTheSendingShouldGetNothing() {
        var loan = lend(1L, 20L);
        today = DUE.minusDays(1);
        var sender = new LoanNoticeSender(loanRepository, noticeRepository, mail);
        var reader = new ReaderContact("Maria", "maria@example.com");
        var returned = loanRepository.findById(loan.getId()).orElseThrow();
        returned.returnCopy(today);
        loanRepository.save(returned);

        assertThat(sender.send(loan.getId(), dueSoon(), 2, today, reader, null)).isFalse();
        assertThat(sender.send(999L, dueSoon(), 2, today, reader, null)).isFalse();
        assertThat(mail.sent).isEmpty();
        assertThat(noticeRepository.all()).isEmpty();
    }

    @Test
    void shouldNotSendAnythingWhenThereAreNoLoans() {
        assertThat(service.sendNotices()).isZero();
        assertThat(mail.sent).isEmpty();
    }
}
