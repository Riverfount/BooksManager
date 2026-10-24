package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.DeactivateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetUserPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserCommand;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the user use cases report to the audit trail (RF27): which operation, on whom, and, as important, what is
 * never reported (a password, a hash, the login typed by someone who is not a user).
 */
class UserServiceAuditTest {

    private static final String PASSWORD = "S3cret-Pass#1";
    private static final String NEW_PASSWORD = "N3w-Pass#word";
    private static final Instant START = Instant.parse("2026-10-15T12:00:00Z");

    /** Stands for BCrypt: never returns the raw password. */
    private static final PasswordHasher HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "{h}" + new StringBuilder(rawPassword).reverse();
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return rawPassword != null && storedHash != null && storedHash.equals(hash(rawPassword));
        }
    };

    private InMemoryUserRepository repository;
    private RecordedAudit audit;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        audit = new RecordedAudit();
        service = new UserService(repository, HASHER, Clock.fixed(START, ZoneOffset.UTC),
                new LoginLockoutProperties(3, Duration.ofMinutes(15)), userId -> 0, audit);
    }

    private User register(String login, Role role) {
        return repository.save(User.create(login, HASHER.hash(PASSWORD), role));
    }

    private void wrongPassword(String login) {
        assertThatThrownBy(() -> service.authenticate(new AuthenticateUserCommand(login, "wrong-Pass#1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private String everythingReported() {
        return audit.all().toString();
    }

    // creating

    @Test
    void shouldReportTheInitialAdministratorWithoutAnActor() {
        service.createInitialAdmin(new CreateInitialAdminCommand("admin", PASSWORD));

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.USER_CREATED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.USER);
        assertThat(recorded.event().targetId()).isEqualTo(repository.findByLogin("admin").orElseThrow().getId());
        assertThat(recorded.event().summary()).contains("administrador inicial");
        assertThat(everythingReported()).doesNotContain(PASSWORD).doesNotContain("{h}");
    }

    @Test
    void shouldReportNothingWhenThereIsNoInitialAdministratorToCreate() {
        register("operator", Role.OPERATOR);

        service.createInitialAdmin(new CreateInitialAdminCommand("admin", PASSWORD));
        service.createInitialAdmin(new CreateInitialAdminCommand(null, null));

        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldReportTheCreatedUserWithItsLoginAndProfileButNeverThePassword() {
        var created = service.create(new CreateUserCommand("maria", NEW_PASSWORD, Role.OPERATOR, "Maria Silva"));

        var event = audit.only();
        assertThat(audit.all().getFirst().asCaller()).isTrue();
        assertThat(event.action()).isEqualTo(AuditAction.USER_CREATED);
        assertThat(event.targetId()).isEqualTo(created.getId());
        assertThat(event.summary()).isEqualTo("login: maria, perfil: OPERATOR");
        assertThat(everythingReported()).doesNotContain(NEW_PASSWORD).doesNotContain("{h}")
                .doesNotContain("Maria Silva");
    }

    @Test
    void shouldReportNothingWhenTheUserIsRefused() {
        register("maria", Role.OPERATOR);

        assertThatThrownBy(() -> service.create(new CreateUserCommand("maria", NEW_PASSWORD, Role.OPERATOR, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.create(new CreateUserCommand("joao", "weak", Role.OPERATOR, null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(audit.isEmpty()).isTrue();
    }

    // editing

    @Test
    void shouldReportAProfileChangeWithTheOldAndTheNewProfile() {
        register("boss", Role.ADMIN);
        var maria = register("maria", Role.OPERATOR);

        service.update(new UpdateUserCommand(maria.getId(), Role.ADMIN, null));

        var event = audit.only();
        assertThat(event.action()).isEqualTo(AuditAction.USER_UPDATED);
        assertThat(event.targetId()).isEqualTo(maria.getId());
        assertThat(event.summary()).isEqualTo("perfil: OPERATOR → ADMIN");
    }

    @Test
    void shouldReportADisplayNameChangeWithoutTheName() {
        var maria = register("maria", Role.OPERATOR);

        service.update(new UpdateUserCommand(maria.getId(), null, "Maria Silva"));

        assertThat(audit.only().summary()).isEqualTo("nome de exibição alterado");
        assertThat(everythingReported()).doesNotContain("Maria Silva");
    }

    @Test
    void shouldReportBothChangesInOneEntry() {
        register("boss", Role.ADMIN);
        var maria = register("maria", Role.OPERATOR);

        service.update(new UpdateUserCommand(maria.getId(), Role.ADMIN, "Maria"));

        assertThat(audit.only().summary()).isEqualTo("perfil: OPERATOR → ADMIN; nome de exibição alterado");
    }

    @Test
    void shouldReportNothingWhenTheEditChangesNothing() {
        var maria = service.create(new CreateUserCommand("maria", NEW_PASSWORD, Role.OPERATOR, "Maria"));
        var before = audit.all().size();

        service.update(new UpdateUserCommand(maria.getId(), Role.OPERATOR, "Maria"));
        service.update(new UpdateUserCommand(maria.getId(), null, null));

        assertThat(audit.all()).hasSize(before);
    }

    @Test
    void shouldReportNothingWhenTheEditIsRefused() {
        var onlyAdmin = register("boss", Role.ADMIN);

        assertThatThrownBy(() -> service.update(new UpdateUserCommand(onlyAdmin.getId(), Role.OPERATOR, null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(audit.isEmpty()).isTrue();
    }

    // deactivating, unblocking and resetting the password

    @Test
    void shouldReportTheDeactivationOnce() {
        register("boss", Role.ADMIN);
        var maria = register("maria", Role.OPERATOR);

        service.deactivate(new DeactivateUserCommand(maria.getId(), 1L));
        service.deactivate(new DeactivateUserCommand(maria.getId(), 1L));

        assertThat(audit.only().action()).isEqualTo(AuditAction.USER_DEACTIVATED);
        assertThat(audit.only().targetId()).isEqualTo(maria.getId());
    }

    @Test
    void shouldReportNothingWhenTheDeactivationIsRefused() {
        var boss = register("boss", Role.ADMIN);

        assertThatThrownBy(() -> service.deactivate(new DeactivateUserCommand(boss.getId(), boss.getId())))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldReportTheUnblockOfABlockedUserOnly() {
        var maria = register("maria", Role.OPERATOR);
        service.unblock(maria.getId());
        assertThat(audit.isEmpty()).isTrue();

        wrongPassword("maria");
        wrongPassword("maria");
        wrongPassword("maria");
        var before = audit.all().size();
        service.unblock(maria.getId());

        assertThat(audit.all()).hasSize(before + 1);
        assertThat(audit.events().getLast().action()).isEqualTo(AuditAction.USER_UNBLOCKED);
        assertThat(audit.events().getLast().targetId()).isEqualTo(maria.getId());
    }

    @Test
    void shouldReportThePasswordResetButNeverThePassword() {
        var maria = register("maria", Role.OPERATOR);

        service.resetPassword(new ResetUserPasswordCommand(maria.getId(), NEW_PASSWORD));

        var event = audit.only();
        assertThat(event.action()).isEqualTo(AuditAction.USER_PASSWORD_RESET);
        assertThat(event.targetId()).isEqualTo(maria.getId());
        assertThat(event.summary()).isNull();
        assertThat(everythingReported()).doesNotContain(NEW_PASSWORD).doesNotContain("{h}");
    }

    @Test
    void shouldReportNothingWhenThePasswordResetIsRefused() {
        var maria = register("maria", Role.OPERATOR);

        assertThatThrownBy(() -> service.resetPassword(new ResetUserPasswordCommand(maria.getId(), "weak")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(audit.isEmpty()).isTrue();
    }

    // logins

    @Test
    void shouldReportASuccessfulLoginAsTheUserWhoLoggedIn() {
        var maria = register("maria", Role.OPERATOR);

        service.authenticate(new AuthenticateUserCommand("maria", PASSWORD));

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isFalse();
        assertThat(recorded.actorId()).isEqualTo(maria.getId());
        assertThat(recorded.event().action()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.USER);
        assertThat(recorded.event().targetId()).isEqualTo(maria.getId());
    }

    @Test
    void shouldReportAWrongPasswordAsAFailureOfThatUserWithoutThePassword() {
        var maria = register("maria", Role.OPERATOR);

        wrongPassword("maria");

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.actorId()).isEqualTo(maria.getId());
        assertThat(recorded.event().action()).isEqualTo(AuditAction.LOGIN_FAILURE);
        assertThat(recorded.event().targetId()).isEqualTo(maria.getId());
        assertThat(recorded.event().summary()).isEqualTo("senha incorreta");
        assertThat(everythingReported()).doesNotContain("wrong-Pass#1").doesNotContain(PASSWORD);
    }

    @Test
    void shouldNeverStoreTheLoginTypedForAnUnknownUser() {
        // a password typed in the login field would otherwise end up in the trail
        assertThatThrownBy(() -> service.authenticate(new AuthenticateUserCommand("my-secret-typed-here", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isFalse();
        assertThat(recorded.actorId()).isNull();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.LOGIN_FAILURE);
        assertThat(recorded.event().target()).isNull();
        assertThat(recorded.event().targetId()).isNull();
        assertThat(recorded.event().summary()).isEqualTo("login desconhecido");
        assertThat(everythingReported()).doesNotContain("my-secret-typed-here").doesNotContain(PASSWORD);
    }

    @Test
    void shouldReportTheLockoutOnTheAttemptThatBlockedTheUserAndOnlyThen() {
        var maria = register("maria", Role.OPERATOR);

        wrongPassword("maria");
        wrongPassword("maria");
        assertThat(audit.actions()).containsExactly(AuditAction.LOGIN_FAILURE, AuditAction.LOGIN_FAILURE);

        wrongPassword("maria");

        assertThat(audit.actions()).containsExactly(AuditAction.LOGIN_FAILURE, AuditAction.LOGIN_FAILURE,
                AuditAction.LOGIN_FAILURE, AuditAction.LOGIN_LOCKOUT);
        var lockout = audit.all().getLast();
        assertThat(lockout.actorId()).isEqualTo(maria.getId());
        assertThat(lockout.event().targetId()).isEqualTo(maria.getId());
        assertThat(lockout.event().summary()).contains("3 tentativas").contains("2026-10-15T12:15:00Z");
    }

    @Test
    void anAttemptWhileBlockedShouldBeAFailureButNotANewLockout() {
        register("maria", Role.OPERATOR);
        wrongPassword("maria");
        wrongPassword("maria");
        wrongPassword("maria");
        var before = audit.all().size();

        wrongPassword("maria");
        assertThatThrownBy(() -> service.authenticate(new AuthenticateUserCommand("maria", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        var added = audit.all().subList(before, audit.all().size());
        assertThat(added).extracting(r -> r.event().action())
                .containsExactly(AuditAction.LOGIN_FAILURE, AuditAction.LOGIN_FAILURE);
        assertThat(added.get(1).event().summary()).isEqualTo("usuário bloqueado");
    }

    @Test
    void theRightPasswordOfAnInactiveUserShouldBeReportedAsAFailure() {
        var maria = register("maria", Role.OPERATOR);
        maria.deactivate();
        repository.save(maria);

        assertThatThrownBy(() -> service.authenticate(new AuthenticateUserCommand("maria", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(audit.only().action()).isEqualTo(AuditAction.LOGIN_FAILURE);
        assertThat(audit.only().summary()).isEqualTo("usuário inativo");
    }
}
