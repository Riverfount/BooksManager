package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.ChangeStaffPasswordCommand;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An administrator or an operator changes their own password (RF09): the current password is asked for and a wrong one
 * counts toward the login lockout, exactly as the reader's change does; the new password follows the policy; every
 * session of the user ends; and the audit trail gets the fact and never a secret.
 */
class StaffPasswordServiceTest {

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

    private InMemoryUserRepository users;
    private RecordedAudit audit;
    private final List<Long> revokedFor = new ArrayList<>();
    private StaffPasswordService service;
    private Long operatorId;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        audit = new RecordedAudit();
        revokedFor.clear();
        RevokeRefreshTokensUseCase refreshTokens = id -> {
            revokedFor.add(id);
            return 1;
        };
        service = new StaffPasswordService(users, new FakeHasher(), refreshTokens, audit,
                Clock.fixed(NOW, ZoneOffset.UTC), new LoginLockoutProperties(3, Duration.ofMinutes(15)));
        operatorId = users.save(User.create("maria", "{h}" + PASSWORD, Role.OPERATOR)).getId();
    }

    private ChangeStaffPasswordCommand change(Long id, String current, String next) {
        return new ChangeStaffPasswordCommand(id, current, next);
    }

    private User user() {
        return users.findById(operatorId).orElseThrow();
    }

    // the change

    @Test
    void shouldChangeThePasswordOfAnOperatorAndOfAnAdministrator() {
        var adminId = users.save(User.create("chefe", "{h}" + PASSWORD, Role.ADMIN)).getId();

        service.change(change(operatorId, PASSWORD, NEW_PASSWORD));
        service.change(change(adminId, PASSWORD, NEW_PASSWORD));

        assertThat(user().getPasswordHash()).isEqualTo("{h}" + NEW_PASSWORD);
        assertThat(users.findById(adminId).orElseThrow().getPasswordHash()).isEqualTo("{h}" + NEW_PASSWORD);
    }

    @Test
    void shouldChangeOnlyThePasswordOfTheUserOfTheCommand() {
        var other = users.save(User.create("joao", "{h}" + PASSWORD, Role.OPERATOR)).getId();

        service.change(change(operatorId, PASSWORD, NEW_PASSWORD));

        assertThat(users.findById(other).orElseThrow().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
    }

    @Test
    void shouldEndEveryRefreshTokenOfTheUserAndOnlyOfHim() {
        users.save(User.create("joao", "{h}" + PASSWORD, Role.OPERATOR));

        service.change(change(operatorId, PASSWORD, NEW_PASSWORD));

        assertThat(revokedFor).containsExactly(operatorId);
    }

    @Test
    void shouldKeepTheProfileTheLoginAndTheActiveState() {
        service.change(change(operatorId, PASSWORD, NEW_PASSWORD));

        assertThat(user().getRole()).isEqualTo(Role.OPERATOR);
        assertThat(user().getLogin()).isEqualTo("maria");
        assertThat(user().isActive()).isTrue();
    }

    @Test
    void shouldResetTheCountOfWrongPasswordsWhenTheCurrentOneIsRight() {
        assertThatThrownBy(() -> service.change(change(operatorId, "errada", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(user().getFailedLoginAttempts()).isEqualTo(1);

        service.change(change(operatorId, PASSWORD, NEW_PASSWORD));

        assertThat(user().getFailedLoginAttempts()).isZero();
    }

    // the audit trail

    @Test
    void shouldReportTheChangeWithTheUserAsTargetAndWithoutAnySecret() {
        service.change(change(operatorId, PASSWORD, NEW_PASSWORD));

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.USER_PASSWORD_CHANGED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.USER);
        assertThat(recorded.event().targetId()).isEqualTo(operatorId);
        assertThat(recorded.event().summary()).isNull();
        assertThat(audit.all().toString()).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD)
                .doesNotContain("{h}");
    }

    // the current password and the lockout (RNF13)

    @Test
    void shouldRefuseAWrongCurrentPasswordCountItAndKeepTheOldOne() {
        assertThatThrownBy(() -> service.change(change(operatorId, "errada", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("a senha atual está incorreta");

        assertThat(user().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
        assertThat(user().getFailedLoginAttempts()).isEqualTo(1);
        assertThat(revokedFor).isEmpty();
    }

    @Test
    void shouldReportAWrongCurrentPasswordAsALoginFailureWithoutTheValue() {
        assertThatThrownBy(() -> service.change(change(operatorId, "errada", NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);

        var event = audit.only();
        assertThat(event.action()).isEqualTo(AuditAction.LOGIN_FAILURE);
        assertThat(event.targetId()).isEqualTo(operatorId);
        assertThat(event.summary()).isEqualTo("senha atual incorreta");
        assertThat(audit.all().toString()).doesNotContain("errada").doesNotContain(NEW_PASSWORD);
    }

    @Test
    void shouldLockTheAccountAfterTooManyWrongPasswordsAndReportTheLockout() {
        for (var attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.change(change(operatorId, "errada", NEW_PASSWORD)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThat(user().isBlocked(NOW)).isTrue();
        assertThat(audit.actions()).containsExactly(AuditAction.LOGIN_FAILURE, AuditAction.LOGIN_FAILURE,
                AuditAction.LOGIN_FAILURE, AuditAction.LOGIN_LOCKOUT);
    }

    @Test
    void shouldRefuseToChangeThePasswordWhileTheAccountIsLockedOutEvenWithTheRightOne() {
        for (var attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.change(change(operatorId, "errada", NEW_PASSWORD)))
                    .isInstanceOf(BusinessRuleException.class);
        }
        var eventsBefore = audit.all().size();

        assertThatThrownBy(() -> service.change(change(operatorId, PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("bloqueada");

        assertThat(user().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
        assertThat(audit.all()).hasSize(eventsBefore);
    }

    // the new password

    @Test
    void shouldRefuseAWeakNewPasswordSayingWhatIsMissingWithoutCountingAnAttempt() {
        assertThatThrownBy(() -> service.change(change(operatorId, PASSWORD, "fraca")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("a senha não atende às regras");

        assertThat(user().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
        assertThat(user().getFailedLoginAttempts()).isZero();
        assertThat(audit.isEmpty()).isTrue();
        assertThat(revokedFor).isEmpty();
    }

    // who may

    @Test
    void shouldRefuseAnInactiveUser() {
        var inactive = users.save(User.reconstitute(null, "joao", "{h}" + PASSWORD, Role.OPERATOR, false));

        assertThatThrownBy(() -> service.change(change(inactive.getId(), PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(users.findById(inactive.getId()).orElseThrow().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldRefuseAReaderAccessWhichChangesThePasswordInTheReaderArea() {
        var access = users.save(User.createReaderAccess("ana", "{h}" + PASSWORD, 42L));

        assertThatThrownBy(() -> service.change(change(access.getId(), PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(users.findById(access.getId()).orElseThrow().getPasswordHash()).isEqualTo("{h}" + PASSWORD);
    }

    @Test
    void shouldRefuseAnUnknownUser() {
        assertThatThrownBy(() -> service.change(change(9_999L, PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldNotLeakPasswordsThroughTheStringFormOfTheCommand() {
        assertThat(change(operatorId, PASSWORD, NEW_PASSWORD).toString()).doesNotContain(PASSWORD)
                .doesNotContain(NEW_PASSWORD);
    }
}
