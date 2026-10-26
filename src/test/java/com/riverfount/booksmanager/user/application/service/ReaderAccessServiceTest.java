package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.application.port.out.RateLimitResult;
import com.riverfount.booksmanager.shared.application.port.out.RateLimiter;
import com.riverfount.booksmanager.shared.configuration.RateLimitProperties;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.AcceptReaderInvitationCommand;
import com.riverfount.booksmanager.user.application.port.in.InviteReaderAccessCommand;
import com.riverfount.booksmanager.user.application.port.in.RequestReaderPasswordResetCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetReaderPasswordCommand;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.ReaderAccessTokenGenerator;
import com.riverfount.booksmanager.user.configuration.ReaderAccessProperties;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.Reader;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The reader access use cases (Phase 6): invitation, acceptance, password reset and revocation. */
class ReaderAccessServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final String PASSWORD = "Senha-forte-123";

    private static final class SequenceTokens implements ReaderAccessTokenGenerator {
        private int next = 1;

        @Override
        public String generate() {
            return "token-" + next++;
        }
    }

    private static final class FakeHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "{h}" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return storedHash.equals("{h}" + rawPassword);
        }
    }

    private static final class RecordingSender implements NotificationSender {
        final List<Notification> sent = new ArrayList<>();

        @Override
        public void send(Notification notification) {
            sent.add(notification);
        }

        @Override
        public void sendAndConfirm(Notification notification) {
            sent.add(notification);
        }
    }

    private InMemoryReaderRepository readers;
    private InMemoryUserRepository users;
    private InMemoryReaderAccessTokenRepository tokens;
    private RecordingSender sender;
    private RecordedAudit audit;
    private final List<Long> revokedFor = new ArrayList<>();
    /** How many attempts each key made: stands for the limiter, which allows three per hour. */
    private final Map<String, Integer> attempts = new HashMap<>();
    private final RateLimiter limiter = (policy, key) -> attempts.merge(key, 1, Integer::sum) <= policy.maxAttempts()
            ? RateLimitResult.allow() : RateLimitResult.refuse(Duration.ofHours(1));
    private ReaderAccessService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        readers = new InMemoryReaderRepository();
        users = new InMemoryUserRepository();
        tokens = new InMemoryReaderAccessTokenRepository();
        sender = new RecordingSender();
        audit = new RecordedAudit();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = newService(clock);
    }

    private ReaderAccessService newService(Clock theClock) {
        RevokeRefreshTokensUseCase refreshTokens = userId -> {
            revokedFor.add(userId);
            return 1;
        };
        return new ReaderAccessService(readers, users, tokens, new SequenceTokens(), new FakeHasher(), sender,
                refreshTokens, audit, theClock, limiter,
                new RateLimitProperties(new RateLimitProperties.Limit(120, Duration.ofMinutes(1)),
                        new RateLimitProperties.Limit(3, Duration.ofHours(1))),
                new ReaderAccessProperties(Duration.ofHours(72), Duration.ofHours(1),
                        "https://app.example.com/convite?token={token}",
                        "https://app.example.com/senha?token={token}"),
                new LoginLockoutProperties(3, Duration.ofMinutes(15)));
    }

    private Long reader(String document, String email) {
        return readers.save(Reader.create("Ana Souza", document, email, null, LocalDate.of(1990, 5, 10), null,
                LocalDate.of(2026, 10, 26))).getId();
    }

    private Long reader() {
        return reader("111.111.111-11", "ana@example.com");
    }

    private Long invitedAndAccepted(Long readerId, String login) {
        service.invite(new InviteReaderAccessCommand(readerId));
        var token = "token-" + tokenCounter();
        service.accept(new AcceptReaderInvitationCommand(token, login, PASSWORD));
        return users.findByLogin(login).orElseThrow().getId();
    }

    /** The token the generator handed out last: the tests read it from the link in the e-mail. */
    private int tokenCounter() {
        var link = sender.sent.getLast().text();
        var start = link.indexOf("token=") + "token=".length();
        var end = start;
        while (end < link.length() && !Character.isWhitespace(link.charAt(end))) {
            end++;
        }
        return Integer.parseInt(link.substring(start, end).replace("token-", ""));
    }

    private String lastTokenInTheMail() {
        return "token-" + tokenCounter();
    }

    // invitation

    @Test
    void shouldInviteAReaderByEmailWithALinkValidFor72Hours() {
        var id = reader();

        var invitation = service.invite(new InviteReaderAccessCommand(id));

        assertThat(invitation.readerId()).isEqualTo(id);
        assertThat(invitation.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
        assertThat(sender.sent).hasSize(1);
        var mail = sender.sent.getFirst();
        assertThat(mail.recipient()).isEqualTo("ana@example.com");
        assertThat(mail.text()).contains("https://app.example.com/convite?token=token-1");
    }

    @Test
    void shouldKeepOnlyTheHashOfTheTokenAndNeverAuditIt() {
        var id = reader();

        service.invite(new InviteReaderAccessCommand(id));

        assertThat(tokens.findByTokenHashForUpdate("token-1")).isEmpty();
        assertThat(tokens.findByTokenHashForUpdate(ReaderAccessToken.hashOf("token-1"))).isPresent();
        assertThat(audit.all().toString()).doesNotContain("token-1").doesNotContain("ana@example.com");
    }

    @Test
    void shouldReportTheInvitationToTheAuditTrailAsDoneByTheCaller() {
        var id = reader();

        service.invite(new InviteReaderAccessCommand(id));

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.READER_ACCESS_INVITED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.READER);
        assertThat(recorded.event().targetId()).isEqualTo(id);
    }

    @Test
    void shouldRefuseToInviteAnUnknownReader() {
        assertThatThrownBy(() -> service.invite(new InviteReaderAccessCommand(999L)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void shouldRefuseToInviteAnInactiveReader() {
        var id = reader();
        var inactive = readers.findById(id).orElseThrow();
        inactive.deactivate();
        readers.save(inactive);

        assertThatThrownBy(() -> service.invite(new InviteReaderAccessCommand(id)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inativo");
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void shouldRefuseToInviteAReaderThatAlreadyHasAnAccess() {
        var id = reader();
        invitedAndAccepted(id, "ana");

        assertThatThrownBy(() -> service.invite(new InviteReaderAccessCommand(id)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já tem acesso");
    }

    @Test
    void aNewInvitationShouldReplaceThePreviousOne() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        var first = lastTokenInTheMail();

        service.invite(new InviteReaderAccessCommand(id));

        assertThatThrownBy(() -> service.accept(new AcceptReaderInvitationCommand(first, "ana", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
        service.accept(new AcceptReaderInvitationCommand(lastTokenInTheMail(), "ana", PASSWORD));
        assertThat(users.findByLogin("ana")).isPresent();
    }

    // acceptance

    @Test
    void shouldCreateTheReaderAccessWhenTheInvitationIsAccepted() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));

        service.accept(new AcceptReaderInvitationCommand(lastTokenInTheMail(), "Ana.Souza", PASSWORD));

        var user = users.findByLogin("ana.souza").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.READER);
        assertThat(user.getReaderId()).isEqualTo(id);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("{h}" + PASSWORD);
    }

    @Test
    void shouldReportTheGrantAsDoneByTheNewUserWithoutSecrets() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        audit = new RecordedAudit();
        service = newService(clock);

        service.accept(new AcceptReaderInvitationCommand(lastTokenInTheMail(), "ana", PASSWORD));

        var userId = users.findByLogin("ana").orElseThrow().getId();
        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isFalse();
        assertThat(recorded.actorId()).isEqualTo(userId);
        assertThat(recorded.event().action()).isEqualTo(AuditAction.READER_ACCESS_GRANTED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.USER);
        assertThat(recorded.event().targetId()).isEqualTo(userId);
        assertThat(audit.all().toString()).doesNotContain(PASSWORD).doesNotContain("token-");
    }

    @Test
    void shouldAcceptAnInvitationOnlyOnce() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        var token = lastTokenInTheMail();
        service.accept(new AcceptReaderInvitationCommand(token, "ana", PASSWORD));

        assertThatThrownBy(() -> service.accept(new AcceptReaderInvitationCommand(token, "outra", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(users.findByLogin("outra")).isEmpty();
    }

    @Test
    void shouldRefuseAnExpiredInvitation() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        var token = lastTokenInTheMail();
        var later = newService(Clock.fixed(NOW.plus(Duration.ofHours(72)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.accept(new AcceptReaderInvitationCommand(token, "ana", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldGiveTheSameAnswerForAnUnknownBlankOrWrongPurposeToken() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        var resetToken = lastTokenInTheMail();

        var unknown = catchMessage(() -> service.accept(new AcceptReaderInvitationCommand("nope", "x", PASSWORD)));
        var blank = catchMessage(() -> service.accept(new AcceptReaderInvitationCommand(" ", "x", PASSWORD)));
        var nulled = catchMessage(() -> service.accept(new AcceptReaderInvitationCommand(null, "x", PASSWORD)));
        var wrongPurpose = catchMessage(() -> service.accept(
                new AcceptReaderInvitationCommand(resetToken, "x", PASSWORD)));

        assertThat(List.of(blank, nulled, wrongPurpose)).containsOnly(unknown);
    }

    @Test
    void shouldRefuseAWeakPasswordWithoutUsingTheInvitation() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        var token = lastTokenInTheMail();

        assertThatThrownBy(() -> service.accept(new AcceptReaderInvitationCommand(token, "ana", "123")))
                .isInstanceOf(BusinessRuleException.class);

        service.accept(new AcceptReaderInvitationCommand(token, "ana", PASSWORD));
        assertThat(users.findByLogin("ana")).isPresent();
    }

    @Test
    void shouldRefuseALoginThatIsTakenWithoutUsingTheInvitation() {
        users.save(User.create("ana", "{h}x", Role.OPERATOR));
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        var token = lastTokenInTheMail();

        assertThatThrownBy(() -> service.accept(new AcceptReaderInvitationCommand(token, "ana", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("login");

        service.accept(new AcceptReaderInvitationCommand(token, "ana.souza", PASSWORD));
        assertThat(users.findByLogin("ana.souza")).isPresent();
    }

    @Test
    void shouldRefuseTheInvitationOfAReaderDeactivatedAfterwards() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        var token = lastTokenInTheMail();
        var inactive = readers.findById(id).orElseThrow();
        inactive.deactivate();
        readers.save(inactive);

        assertThatThrownBy(() -> service.accept(new AcceptReaderInvitationCommand(token, "ana", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(users.findByLogin("ana")).isEmpty();
    }

    // password reset

    @Test
    void shouldSendTheResetLinkToTheReaderThatHasAnAccess() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        sender.sent.clear();

        service.requestReset(new RequestReaderPasswordResetCommand("  ANA@example.com "));

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.getFirst().recipient()).isEqualTo("ana@example.com");
        assertThat(sender.sent.getFirst().text()).contains("https://app.example.com/senha?token=");
    }

    @Test
    void shouldSendNothingAndSayNothingForAnUnknownEmailOrAReaderWithoutAccess() {
        reader();

        service.requestReset(new RequestReaderPasswordResetCommand("ninguem@example.com"));
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        service.requestReset(new RequestReaderPasswordResetCommand(null));
        service.requestReset(new RequestReaderPasswordResetCommand(" "));

        assertThat(sender.sent).isEmpty();
    }

    @Test
    void shouldSendNothingToAReaderWhoseAccessWasRevoked() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        service.revoke(id);
        sender.sent.clear();

        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));

        assertThat(sender.sent).isEmpty();
    }

    @Test
    void shouldChangeThePasswordWithTheResetLinkAndRevokeTheSessions() {
        var id = reader();
        var userId = invitedAndAccepted(id, "ana");
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        audit = new RecordedAudit();
        service = newService(clock);
        revokedFor.clear();

        service.reset(new ResetReaderPasswordCommand(lastTokenInTheMail(), "Outra-senha-456"));

        assertThat(users.findById(userId).orElseThrow().getPasswordHash()).isEqualTo("{h}Outra-senha-456");
        assertThat(revokedFor).containsExactly(userId);
        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isFalse();
        assertThat(recorded.actorId()).isEqualTo(userId);
        assertThat(recorded.event().action()).isEqualTo(AuditAction.READER_PASSWORD_RESET);
        assertThat(audit.all().toString()).doesNotContain("Outra-senha-456").doesNotContain("token-");
    }

    @Test
    void shouldLiftTheLoginLockoutWhenThePasswordIsReset() {
        var id = reader();
        var userId = invitedAndAccepted(id, "ana");
        var locked = users.findById(userId).orElseThrow();
        locked.registerFailedLogin(NOW, 1, Duration.ofHours(1));
        users.save(locked);
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));

        service.reset(new ResetReaderPasswordCommand(lastTokenInTheMail(), "Outra-senha-456"));

        assertThat(users.findById(userId).orElseThrow().isBlocked(NOW)).isFalse();
    }

    @Test
    void shouldUseTheResetLinkOnlyOnce() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        var token = lastTokenInTheMail();
        service.reset(new ResetReaderPasswordCommand(token, "Outra-senha-456"));

        assertThatThrownBy(() -> service.reset(new ResetReaderPasswordCommand(token, "Terceira-senha-789")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRefuseAnExpiredResetLinkAndAnInvitationUsedAsResetLink() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        var token = lastTokenInTheMail();
        var later = newService(Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.reset(new ResetReaderPasswordCommand(token, "Outra-senha-456")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reset(new ResetReaderPasswordCommand("token-1", "Outra-senha-456")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRefuseTheResetLinkOfAReaderWhoseAccessWasRevoked() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        var token = lastTokenInTheMail();
        service.revoke(id);

        assertThatThrownBy(() -> service.reset(new ResetReaderPasswordCommand(token, "Outra-senha-456")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRefuseAWeakNewPasswordWithoutUsingTheResetLink() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        var token = lastTokenInTheMail();

        assertThatThrownBy(() -> service.reset(new ResetReaderPasswordCommand(token, "123")))
                .isInstanceOf(BusinessRuleException.class);

        service.reset(new ResetReaderPasswordCommand(token, "Outra-senha-456"));
    }

    // revocation

    @Test
    void shouldRevokeTheAccessAndTheSessionsOfTheReader() {
        var id = reader();
        var userId = invitedAndAccepted(id, "ana");
        audit = new RecordedAudit();
        service = newService(clock);
        revokedFor.clear();

        service.revoke(id);

        assertThat(users.findById(userId).orElseThrow().isActive()).isFalse();
        assertThat(revokedFor).containsExactly(userId);
        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.READER_ACCESS_REVOKED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.USER);
        assertThat(recorded.event().targetId()).isEqualTo(userId);
    }

    @Test
    void shouldBeIdempotentWhenTheReaderHasNoActiveAccess() {
        var id = reader();

        service.revoke(id);

        assertThat(audit.isEmpty()).isTrue();
        assertThat(revokedFor).isEmpty();
    }

    @Test
    void shouldRefuseToRevokeTheAccessOfAnUnknownReader() {
        assertThatThrownBy(() -> service.revoke(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revokingShouldInvalidateAPendingInvitation() {
        var id = reader();
        service.invite(new InviteReaderAccessCommand(id));
        var token = lastTokenInTheMail();

        service.revoke(id);

        assertThatThrownBy(() -> service.accept(new AcceptReaderInvitationCommand(token, "ana", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aRevokedReaderShouldBeInvitedAgainAndKeepTheOldAccessInactive() {
        var id = reader();
        var oldUserId = invitedAndAccepted(id, "ana");
        service.revoke(id);

        service.invite(new InviteReaderAccessCommand(id));
        service.accept(new AcceptReaderInvitationCommand(lastTokenInTheMail(), "ana2", PASSWORD));

        assertThat(users.findById(oldUserId).orElseThrow().isActive()).isFalse();
        assertThat(users.findByLogin("ana2").orElseThrow().isActive()).isTrue();
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
        } catch (BusinessRuleException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a BusinessRuleException");
    }

    // limits of attempts and personal data (Phase 6, RNF31, LGPD)

    private int mailsSent() {
        return sender.sent.size();
    }

    @Test
    void shouldSendNoMoreResetLinksOnceAnEmailIsOverTheLimitAndSayNothingAboutIt() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        sender.sent.clear();

        for (var i = 0; i < 6; i++) {
            service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        }

        // three per hour: the other three requests are answered like any other, with nothing sent
        assertThat(mailsSent()).isEqualTo(3);
    }

    @Test
    void shouldCountTheSameEmailWhateverTheCaseAndTheSpaces() {
        var id = reader();
        invitedAndAccepted(id, "ana");
        sender.sent.clear();

        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
        service.requestReset(new RequestReaderPasswordResetCommand("  ANA@Example.com "));
        service.requestReset(new RequestReaderPasswordResetCommand("Ana@EXAMPLE.com"));
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com "));

        assertThat(mailsSent()).isEqualTo(3);
    }

    @Test
    void shouldCountAnUnknownEmailToo_soTheLimitDoesNotTellWhichEmailsExist() {
        for (var i = 0; i < 4; i++) {
            service.requestReset(new RequestReaderPasswordResetCommand("ninguem@example.com"));
        }

        assertThat(attempts.values()).containsExactly(4);
        assertThat(mailsSent()).isZero();
    }

    @Test
    void shouldNotKeepTheEmailItselfAsTheKeyOfTheLimit() {
        service.requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));

        assertThat(attempts.keySet()).hasSize(1)
                .allSatisfy(key -> assertThat(key).doesNotContain("ana").doesNotContain("example"));
    }

    @Test
    void shouldNotCountABlankEmail() {
        service.requestReset(new RequestReaderPasswordResetCommand(" "));
        service.requestReset(new RequestReaderPasswordResetCommand(null));

        assertThat(attempts).isEmpty();
    }

    @Test
    void shouldNotMentionTheEmailInTheStringFormOfTheRequest() {
        assertThat(new RequestReaderPasswordResetCommand("ana@example.com").toString())
                .doesNotContain("ana@example.com");
    }

    @Test
    void aMinorShouldBeInvitedAtTheirOwnEmailAndTheGuardianIsNeverWritten() {
        var minor = readers.save(Reader.create("Caio Souza", "222.222.222-22", "caio@example.com", null,
                LocalDate.of(2012, 3, 4), new Guardian("Paula Souza", "paula@example.com", "11999990000"),
                LocalDate.of(2026, 10, 26))).getId();

        service.invite(new InviteReaderAccessCommand(minor));

        assertThat(sender.sent).hasSize(1);
        var mail = sender.sent.getFirst();
        assertThat(mail.recipient()).isEqualTo("caio@example.com");
        assertThat(mail.text()).doesNotContain("Paula").doesNotContain("paula@example.com");
        assertThat(audit.all().toString()).doesNotContain("Paula").doesNotContain("paula@example.com");
    }
}
