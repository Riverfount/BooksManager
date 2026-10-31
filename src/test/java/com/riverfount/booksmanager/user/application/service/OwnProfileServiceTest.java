package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.ChangeOwnPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateOwnProfileCommand;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The reader maintains their own registration (RF32, RN22): contact data and password. */
class OwnProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final String PASSWORD = "Senha-forte-123";
    private static final String NEW_PASSWORD = "Outra-senha-456";

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

    private InMemoryReaderRepository readers;
    private InMemoryUserRepository users;
    private RecordedAudit audit;
    private final List<Long> revokedFor = new ArrayList<>();
    private OwnProfileService service;
    private Long readerId;
    private Long userId;

    @BeforeEach
    void setUp() {
        readers = new InMemoryReaderRepository();
        users = new InMemoryUserRepository();
        audit = new RecordedAudit();
        RevokeRefreshTokensUseCase refreshTokens = id -> {
            revokedFor.add(id);
            return 1;
        };
        service = new OwnProfileService(readers, users, new FakeHasher(), refreshTokens, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), new LoginLockoutProperties(3, Duration.ofMinutes(15)));
        readerId = readers.save(Reader.create("Ana Souza", "111.111.111-11", "ana@example.com", "11 91234-5678",
                LocalDate.of(1990, 5, 10), null, LocalDate.of(2026, 10, 1))).getId();
        userId = users.save(User.createReaderAccess("ana", "{h}" + PASSWORD, readerId)).getId();
    }

    private UpdateOwnProfileCommand update(String phone, String email, String password) {
        return new UpdateOwnProfileCommand(readerId, phone, email, null, null, null, password);
    }

    private User user() {
        return users.findById(userId).orElseThrow();
    }

    private Reader reader() {
        return readers.findById(readerId).orElseThrow();
    }

    // reading

    @Test
    void shouldShowTheOwnRegistrationAndWhetherTheReaderIsAMinor() {
        var profile = service.get(readerId);

        assertThat(profile.reader().getName()).isEqualTo("Ana Souza");
        assertThat(profile.minor()).isFalse();
    }

    @Test
    void shouldTellThatAReaderUnder18IsAMinor() {
        var minorId = readers.save(Reader.create("Pedro", "222", "pedro@example.com", null,
                LocalDate.of(2012, 3, 1), new Guardian("Carla", "carla@example.com", null),
                LocalDate.of(2026, 10, 1))).getId();

        assertThat(service.get(minorId).minor()).isTrue();
    }

    @Test
    void shouldRefuseToShowAnUnknownReader() {
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // contact data

    @Test
    void shouldChangeThePhoneWithoutAskingForThePassword() {
        var profile = service.update(update("21 98888-7777", null, null));

        assertThat(profile.reader().getPhone()).isEqualTo("21 98888-7777");
        assertThat(reader().getPhone()).isEqualTo("21 98888-7777");
        assertThat(reader().getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void shouldKeepEveryFieldNotSent() {
        service.update(update(null, null, null));

        assertThat(reader().getPhone()).isEqualTo("11 91234-5678");
        assertThat(reader().getEmail()).isEqualTo("ana@example.com");
        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldReportOnlyTheNamesOfTheChangedFieldsToTheAuditTrail() {
        service.update(update("21 98888-7777", null, null));

        var event = audit.only();
        assertThat(event.action()).isEqualTo(AuditAction.READER_PROFILE_UPDATED);
        assertThat(event.target()).isEqualTo(AuditTarget.READER);
        assertThat(event.targetId()).isEqualTo(readerId);
        assertThat(event.summary()).isEqualTo("campos: telefone");
        assertThat(audit.all().toString()).doesNotContain("98888").doesNotContain("ana@example.com");
    }

    @Test
    void shouldNotReportAnythingWhenTheValuesSentAreTheCurrentOnes() {
        service.update(update("11 91234-5678", "ana@example.com", null));

        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldAskForThePasswordToChangeTheEmail() {
        assertThatThrownBy(() -> service.update(update(null, "novo@example.com", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("a senha atual está incorreta");

        assertThat(reader().getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void shouldChangeTheEmailWhenThePasswordIsRight() {
        service.update(update(null, "novo@example.com", PASSWORD));

        assertThat(reader().getEmail()).isEqualTo("novo@example.com");
        assertThat(audit.only().summary()).isEqualTo("campos: e-mail");
        assertThat(audit.all().toString()).doesNotContain("novo@example.com").doesNotContain(PASSWORD);
    }

    @Test
    void shouldNotChangeTheLoginWhenTheEmailChanges() {
        service.update(update(null, "novo@example.com", PASSWORD));

        assertThat(user().getLogin()).isEqualTo("ana");
    }

    @Test
    void shouldCountAWrongPasswordTowardTheLockoutAndReportIt() {
        assertThatThrownBy(() -> service.update(update(null, "novo@example.com", "errada")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(user().getFailedLoginAttempts()).isEqualTo(1);
        var event = audit.only();
        assertThat(event.action()).isEqualTo(AuditAction.LOGIN_FAILURE);
        assertThat(event.summary()).isEqualTo("senha atual incorreta");
        assertThat(audit.all().toString()).doesNotContain("errada");
    }

    @Test
    void shouldLockTheAccountAfterTooManyWrongPasswordsAndRefuseEvenTheRightOneMeanwhile() {
        for (var attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.update(update(null, "novo@example.com", "errada")))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThat(user().isBlocked(NOW)).isTrue();
        assertThat(audit.actions()).contains(AuditAction.LOGIN_LOCKOUT);
        assertThatThrownBy(() -> service.update(update(null, "novo@example.com", PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("bloqueada");
        assertThat(reader().getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void shouldResetTheCountOfWrongPasswordsWhenThePasswordIsRight() {
        assertThatThrownBy(() -> service.update(update(null, "novo@example.com", "errada")))
                .isInstanceOf(BusinessRuleException.class);

        service.update(update(null, "novo@example.com", PASSWORD));

        assertThat(user().getFailedLoginAttempts()).isZero();
    }

    @Test
    void shouldRefuseAnInvalidEmailBeforeLookingAtThePassword() {
        assertThatThrownBy(() -> service.update(update(null, "sem-arroba", "errada")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("e-mail");

        assertThat(user().getFailedLoginAttempts()).isZero();
    }

    @Test
    void shouldRefuseAnInvalidPhoneAndChangeNothing() {
        assertThatThrownBy(() -> service.update(update("abc", null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("telefone");

        assertThat(reader().getPhone()).isEqualTo("11 91234-5678");
    }

    @Test
    void shouldRefuseTheGuardianOfAnAdult() {
        var command = new UpdateOwnProfileCommand(readerId, null, null, "Carla", "carla@example.com", null, null);

        assertThatThrownBy(() -> service.update(command)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");

        assertThat(reader().getGuardian()).isNull();
    }

    @Test
    void shouldChangeTheGuardianOfAMinorFieldByField() {
        var minorId = readers.save(Reader.create("Pedro", "222", "pedro@example.com", null,
                LocalDate.of(2012, 3, 1), new Guardian("Carla", "carla@example.com", "11 90000-1111"),
                LocalDate.of(2026, 10, 1))).getId();

        var profile = service.update(new UpdateOwnProfileCommand(minorId, null, null, null, "nova@example.com",
                null, null));

        assertThat(profile.reader().getGuardian()).isEqualTo(new Guardian("Carla", "nova@example.com",
                "11 90000-1111"));
        assertThat(audit.only().summary()).isEqualTo("campos: responsável");
    }

    @Test
    void shouldRefuseAnInactiveReader() {
        var reader = reader();
        reader.deactivate();
        readers.save(reader);

        assertThatThrownBy(() -> service.update(update("21 98888-7777", null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void shouldRefuseAnUnknownReader() {
        assertThatThrownBy(() -> service.update(new UpdateOwnProfileCommand(999L, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldNotTouchTheIdentityDataOrTheState() {
        service.update(update("21 98888-7777", "novo@example.com", PASSWORD));

        var reader = reader();
        assertThat(reader.getName()).isEqualTo("Ana Souza");
        assertThat(reader.getDocument()).isEqualTo("11111111111");
        assertThat(reader.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 10));
        assertThat(reader.isActive()).isTrue();
        assertThat(reader.isBlocked()).isFalse();
    }

    @Test
    void shouldNotLeakPasswordsThroughTheStringFormOfTheCommands() {
        assertThat(update(null, "a@b.com", PASSWORD).toString()).doesNotContain(PASSWORD).doesNotContain("a@b.com");
        assertThat(new ChangeOwnPasswordCommand(readerId, PASSWORD, NEW_PASSWORD).toString())
                .doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD);
    }

    // password

    @Test
    void shouldChangeThePasswordAndEndEveryOtherSession() {
        service.change(new ChangeOwnPasswordCommand(readerId, PASSWORD, NEW_PASSWORD));

        assertThat(user().getPasswordHash()).isEqualTo("{h}" + NEW_PASSWORD);
        assertThat(revokedFor).containsExactly(userId);
    }

    @Test
    void shouldReportThePasswordChangeWithoutAnySecret() {
        service.change(new ChangeOwnPasswordCommand(readerId, PASSWORD, NEW_PASSWORD));

        var event = audit.only();
        assertThat(event.action()).isEqualTo(AuditAction.READER_PASSWORD_CHANGED);
        assertThat(event.target()).isEqualTo(AuditTarget.USER);
        assertThat(event.targetId()).isEqualTo(userId);
        assertThat(audit.all().toString()).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD).doesNotContain("{h}");
    }

    @Test
    void shouldRefuseAWrongCurrentPasswordCountItAndKeepTheOldOne() {
        assertThatThrownBy(() -> service.change(new ChangeOwnPasswordCommand(readerId, "errada", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("a senha atual está incorreta");

        assertThat(user().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
        assertThat(user().getFailedLoginAttempts()).isEqualTo(1);
        assertThat(revokedFor).isEmpty();
    }

    @Test
    void shouldRefuseAWeakNewPasswordWithoutCountingAnAttempt() {
        assertThatThrownBy(() -> service.change(new ChangeOwnPasswordCommand(readerId, PASSWORD, "fraca")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(user().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
        assertThat(user().getFailedLoginAttempts()).isZero();
        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldRefuseToChangeThePasswordWhileTheAccountIsLockedOut() {
        for (var attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.change(new ChangeOwnPasswordCommand(readerId, "errada", NEW_PASSWORD)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThatThrownBy(() -> service.change(new ChangeOwnPasswordCommand(readerId, PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("bloqueada");
        assertThat(user().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
    }

    @Test
    void shouldRefuseAReaderWithoutAnAccess() {
        var other = readers.save(Reader.create("Bia", "333", "bia@example.com", null, LocalDate.of(1990, 1, 1),
                null, LocalDate.of(2026, 10, 1))).getId();

        assertThatThrownBy(() -> service.change(new ChangeOwnPasswordCommand(other, PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldRefuseAnInactiveReaderToChangeThePassword() {
        var reader = reader();
        reader.deactivate();
        readers.save(reader);

        assertThatThrownBy(() -> service.change(new ChangeOwnPasswordCommand(readerId, PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
