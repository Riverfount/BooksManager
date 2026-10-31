package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.AdminPasswordResetResult;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordCommand;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recovering the access of an administrator who forgot the password, from the environment at startup (RF08, RF09):
 * only an active administrator is touched; the password, the login block and the refresh tokens change together; the
 * audit trail gets the fact and never a secret; and a restart with the variable still set changes nothing.
 */
class AdminPasswordResetServiceTest {

    private static final String NEW_PASSWORD = "Nova#Senha2026";
    private static final String OLD_HASH = "{old}hash-of-the-forgotten-password";

    /** Stands for BCrypt: never returns the raw password and differs on each call, like a salted hash. */
    private static class FakePasswordHasher implements PasswordHasher {
        private int hashed;

        @Override
        public String hash(String rawPassword) {
            return "{hash-" + (++hashed) + "}" + new StringBuilder(rawPassword).reverse();
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return rawPassword != null && storedHash != null
                    && storedHash.endsWith("}" + new StringBuilder(rawPassword).reverse());
        }
    }

    private InMemoryUserRepository users;
    private FakePasswordHasher hasher;
    private RecordedAudit audit;
    private final List<Long> revokedFor = new ArrayList<>();
    private AdminPasswordResetService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        hasher = new FakePasswordHasher();
        audit = new RecordedAudit();
        revokedFor.clear();
        RevokeRefreshTokensUseCase revoker = userId -> {
            revokedFor.add(userId);
            return 1;
        };
        service = new AdminPasswordResetService(users, hasher, revoker, audit);
    }

    private User save(String login, Role role, boolean active) {
        return users.save(User.reconstitute(null, login, OLD_HASH, role, active));
    }

    private AdminPasswordResetResult reset(String login, String password) {
        return service.reset(new ResetAdminPasswordCommand(login, password));
    }

    // the recovery

    @Test
    void shouldReplaceThePasswordOfTheAdministratorAndKeepTheOthersAsTheyWere() {
        var admin = save("admin", Role.ADMIN, true);
        var other = save("outro", Role.ADMIN, true);

        var result = reset("admin", NEW_PASSWORD);

        assertThat(result).isEqualTo(AdminPasswordResetResult.RESET);
        var stored = users.findById(admin.getId()).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(OLD_HASH).doesNotContain(NEW_PASSWORD);
        assertThat(hasher.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(users.findById(other.getId()).orElseThrow().getPasswordHash()).isEqualTo(OLD_HASH);
    }

    @Test
    void shouldFindTheAdministratorIgnoringCaseAndSurroundingSpaces() {
        save("admin", Role.ADMIN, true);

        assertThat(reset("  ADMIN ", NEW_PASSWORD)).isEqualTo(AdminPasswordResetResult.RESET);
    }

    @Test
    void shouldClearTheLoginBlockAndTheFailedAttempts() {
        var blocked = users.save(User.reconstitute(null, "admin", OLD_HASH, Role.ADMIN, true, 3,
                Instant.now().plusSeconds(900)));

        reset("admin", NEW_PASSWORD);

        var stored = users.findById(blocked.getId()).orElseThrow();
        assertThat(stored.getFailedLoginAttempts()).isZero();
        assertThat(stored.getBlockedUntil()).isNull();
    }

    @Test
    void shouldRevokeEveryRefreshTokenOfTheAdministratorAndOnlyOfHim() {
        var admin = save("admin", Role.ADMIN, true);
        save("outro", Role.ADMIN, true);

        reset("admin", NEW_PASSWORD);

        assertThat(revokedFor).containsExactly(admin.getId());
    }

    @Test
    void shouldKeepTheAdministratorActiveAndWithTheSameProfile() {
        var admin = save("admin", Role.ADMIN, true);

        reset("admin", NEW_PASSWORD);

        var stored = users.findById(admin.getId()).orElseThrow();
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.getRole()).isEqualTo(Role.ADMIN);
    }

    // the audit trail

    @Test
    void shouldReportTheResetWithoutActorAndWithoutAnySecret() {
        var admin = save("admin", Role.ADMIN, true);

        reset("admin", NEW_PASSWORD);

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        var event = recorded.event();
        assertThat(event.action()).isEqualTo(AuditAction.USER_PASSWORD_RESET);
        assertThat(event.target()).isEqualTo(AuditTarget.USER);
        assertThat(event.targetId()).isEqualTo(admin.getId());
        assertThat(event.summary()).isEqualTo("senha redefinida na subida, pelo ambiente");
        var stored = users.findById(admin.getId()).orElseThrow();
        assertThat(String.valueOf(event)).doesNotContain(NEW_PASSWORD).doesNotContain(stored.getPasswordHash())
                .doesNotContain("admin\"");
    }

    // a restart with the variable still set

    @Test
    void aSecondRunWithTheSamePasswordShouldChangeNothing() {
        var admin = save("admin", Role.ADMIN, true);
        reset("admin", NEW_PASSWORD);
        var hashAfterTheFirstRun = users.findById(admin.getId()).orElseThrow().getPasswordHash();
        revokedFor.clear();

        var second = reset("admin", NEW_PASSWORD);

        assertThat(second).isEqualTo(AdminPasswordResetResult.ALREADY_UP_TO_DATE);
        assertThat(users.findById(admin.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashAfterTheFirstRun);
        assertThat(audit.all()).hasSize(1);
        assertThat(revokedFor).isEmpty();
        assertThat(hasher.hashed).isEqualTo(1);
    }

    @Test
    void aNewDifferentPasswordInTheVariableIsAppliedAgain() {
        var admin = save("admin", Role.ADMIN, true);
        reset("admin", NEW_PASSWORD);

        var again = reset("admin", "Outra#Senha2027");

        assertThat(again).isEqualTo(AdminPasswordResetResult.RESET);
        assertThat(hasher.matches("Outra#Senha2027", users.findById(admin.getId()).orElseThrow().getPasswordHash()))
                .isTrue();
        assertThat(audit.all()).hasSize(2);
    }

    // what is never touched

    @Test
    void shouldNotResetAnOperator() {
        var operator = save("operador", Role.OPERATOR, true);

        var result = reset("operador", NEW_PASSWORD);

        assertThat(result).isEqualTo(AdminPasswordResetResult.NOT_AN_ADMINISTRATOR);
        assertNothingChanged(operator);
    }

    @Test
    void shouldNotResetAReaderAccess() {
        var access = users.save(User.createReaderAccess("leitor", OLD_HASH, 7L));

        var result = reset("leitor", NEW_PASSWORD);

        assertThat(result).isEqualTo(AdminPasswordResetResult.NOT_AN_ADMINISTRATOR);
        assertNothingChanged(access);
    }

    @Test
    void shouldLeaveAnInactiveAdministratorAsHeIsSinceAUserIsNeverReactivated() {
        var inactive = save("admin", Role.ADMIN, false);

        var result = reset("admin", NEW_PASSWORD);

        assertThat(result).isEqualTo(AdminPasswordResetResult.INACTIVE);
        assertNothingChanged(inactive);
        assertThat(users.findById(inactive.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldDoNothingForAnUnknownLogin() {
        var admin = save("admin", Role.ADMIN, true);

        var result = reset("ninguem", NEW_PASSWORD);

        assertThat(result).isEqualTo(AdminPasswordResetResult.NOT_FOUND);
        assertNothingChanged(admin);
    }

    @Test
    void shouldDoNothingWhenTheLoginOrThePasswordIsNotGiven() {
        var admin = save("admin", Role.ADMIN, true);

        assertThat(reset(null, NEW_PASSWORD)).isEqualTo(AdminPasswordResetResult.NOT_CONFIGURED);
        assertThat(reset("  ", NEW_PASSWORD)).isEqualTo(AdminPasswordResetResult.NOT_CONFIGURED);
        assertThat(reset("admin", null)).isEqualTo(AdminPasswordResetResult.NOT_CONFIGURED);
        assertThat(reset("admin", "")).isEqualTo(AdminPasswordResetResult.NOT_CONFIGURED);

        assertNothingChanged(admin);
    }

    @Test
    void shouldRefuseAPasswordThatBreaksThePolicySayingWhatIsMissingButNotTheValue() {
        var admin = save("admin", Role.ADMIN, true);

        assertThatThrownBy(() -> reset("admin", "curta"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("a senha não atende às regras")
                .message().doesNotContain("curta\"");

        assertNothingChanged(admin);
    }

    private void assertNothingChanged(User before) {
        var stored = users.findById(before.getId()).orElseThrow();
        assertThat(stored.getPasswordHash()).isEqualTo(OLD_HASH);
        assertThat(audit.isEmpty()).isTrue();
        assertThat(revokedFor).isEmpty();
        assertThat(hasher.hashed).isZero();
    }
}
