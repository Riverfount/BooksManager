package com.riverfount.booksmanager.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BLOCK = Duration.ofMinutes(15);
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    private static User operator() {
        return User.create("maria", HASH, Role.OPERATOR);
    }

    // roles (RF08, RF09)

    @Test
    void shouldHaveOnlyTheAdminAndOperatorRoles() {
        assertThat(Role.values()).containsExactly(Role.ADMIN, Role.OPERATOR);
    }

    // creation

    @Test
    void shouldCreateActiveUser() {
        var user = User.create("maria", HASH, Role.OPERATOR);

        assertThat(user.getId()).isNull();
        assertThat(user.getLogin()).isEqualTo("maria");
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
        assertThat(user.getRole()).isEqualTo(Role.OPERATOR);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void shouldReconstituteUserWithPersistedState() {
        var user = User.reconstitute(7L, "maria", HASH, Role.ADMIN, false);

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.isActive()).isFalse();
    }

    // login

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldRejectMissingLogin(String login) {
        assertThatThrownBy(() -> User.create(login, HASH, Role.OPERATOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("login");
    }

    @ParameterizedTest
    @ValueSource(strings = {" maria", "maria ", "ma ria", "ma\tria"})
    void shouldRejectLoginWithSpaces(String login) {
        assertThatThrownBy(() -> User.create(login, HASH, Role.OPERATOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("espaços");
    }

    @Test
    void shouldRejectLoginLongerThanTheLimitButAcceptTheLimit() {
        assertThat(User.create("a".repeat(User.MAX_LOGIN_LENGTH), HASH, Role.OPERATOR).getLogin())
                .hasSize(User.MAX_LOGIN_LENGTH);

        assertThatThrownBy(() -> User.create("a".repeat(User.MAX_LOGIN_LENGTH + 1), HASH, Role.OPERATOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("login");
    }

    @Test
    void shouldNormalizeTheLoginToLowercaseSoItIsNotCaseSensitive() {
        assertThat(User.create("Maria.Silva", HASH, Role.OPERATOR).getLogin()).isEqualTo("maria.silva");
        assertThat(User.normalizeLogin("  Maria ")).isEqualTo("maria");
        assertThat(User.normalizeLogin(null)).isEmpty();
    }

    // password hash (RNF07)

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectMissingPasswordHash(String hash) {
        assertThatThrownBy(() -> User.create("maria", hash, Role.OPERATOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hash da senha");
    }

    @Test
    void shouldRejectPasswordHashLongerThanTheLimit() {
        assertThatThrownBy(() -> User.create("maria", "x".repeat(User.MAX_PASSWORD_HASH_LENGTH + 1),
                Role.OPERATOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hash da senha");
    }

    @Test
    void shouldNotShowThePasswordHashInTheStringForm() {
        assertThat(operator().toString()).doesNotContain(HASH);
    }

    // role

    @Test
    void shouldRejectMissingRole() {
        assertThatThrownBy(() -> User.create("maria", HASH, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("perfil");
    }

    @Test
    void shouldChangeRole() {
        var user = operator();

        user.changeRole(Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldRejectChangingToAMissingRoleAndKeepTheCurrentOne() {
        var user = operator();

        assertThatThrownBy(() -> user.changeRole(null)).isInstanceOf(BusinessRuleException.class);

        assertThat(user.getRole()).isEqualTo(Role.OPERATOR);
    }

    // password change

    @Test
    void shouldChangePasswordHash() {
        var user = operator();

        user.changePasswordHash("$2a$10$newhashnewhashnewhashnewhashnewhashnewhashnewhashnewha");

        assertThat(user.getPasswordHash()).startsWith("$2a$10$newhash");
    }

    @Test
    void shouldRejectChangingToAMissingPasswordHashAndKeepTheCurrentOne() {
        var user = operator();

        assertThatThrownBy(() -> user.changePasswordHash(" ")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> user.changePasswordHash(null)).isInstanceOf(BusinessRuleException.class);

        assertThat(user.getPasswordHash()).isEqualTo(HASH);
    }

    // deactivation and authentication (RF08, RF09)

    @Test
    void activeUserCanAuthenticate() {
        assertThat(operator().canAuthenticate(NOW)).isTrue();
    }

    @Test
    void deactivatedUserCannotAuthenticate() {
        var user = operator();

        user.deactivate();

        assertThat(user.isActive()).isFalse();
        assertThat(user.canAuthenticate(NOW)).isFalse();
    }

    @Test
    void deactivateShouldBeIdempotent() {
        var user = operator();

        user.deactivate();
        user.deactivate();

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void deactivationShouldKeepTheLoginRoleAndHash() {
        var user = User.create("maria", HASH, Role.ADMIN);

        user.deactivate();

        assertThat(user.getLogin()).isEqualTo("maria");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
    }

    // login lockout (RF08)

    private static User failed(User user, int times, Instant at) {
        for (var i = 0; i < times; i++) {
            user.registerFailedLogin(at, MAX_ATTEMPTS, BLOCK);
        }
        return user;
    }

    @Test
    void shouldStartWithNoFailedAttemptsAndNotBlocked() {
        var user = operator();

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getBlockedUntil()).isNull();
        assertThat(user.isBlocked(NOW)).isFalse();
    }

    @Test
    void shouldCountFailedAttemptsWithoutBlockingBeforeTheLimit() {
        var user = failed(operator(), MAX_ATTEMPTS - 1, NOW);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(user.isBlocked(NOW)).isFalse();
        assertThat(user.canAuthenticate(NOW)).isTrue();
    }

    @Test
    void shouldBlockForTheConfiguredTimeOnTheAttemptThatReachesTheLimit() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(user.getBlockedUntil()).isEqualTo(NOW.plus(BLOCK));
        assertThat(user.isBlocked(NOW)).isTrue();
        assertThat(user.canAuthenticate(NOW)).isFalse();
    }

    @Test
    void theBlockShouldEndExactlyWhenItsTimeIsUp() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);
        var end = NOW.plus(BLOCK);

        assertThat(user.isBlocked(end.minusMillis(1))).isTrue();
        assertThat(user.isBlocked(end)).isFalse();
        assertThat(user.canAuthenticate(end)).isTrue();
        assertThat(user.canAuthenticate(end.plusSeconds(1))).isTrue();
    }

    @Test
    void shouldNotCountNorExtendTheBlockWhileBlocked() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);

        var changed = user.registerFailedLogin(NOW.plusSeconds(60), MAX_ATTEMPTS, BLOCK);

        assertThat(changed).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(user.getBlockedUntil()).isEqualTo(NOW.plus(BLOCK));
    }

    @Test
    void afterTheBlockEndsAFailureStartsTheCountOverFromOne() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);
        var later = NOW.plus(BLOCK).plusSeconds(1);

        user.registerFailedLogin(later, MAX_ATTEMPTS, BLOCK);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getBlockedUntil()).isNull();
        assertThat(user.isBlocked(later)).isFalse();
    }

    @Test
    void afterTheBlockEndsItTakesAFullNewRoundOfAttemptsToBlockAgain() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);
        var later = NOW.plus(BLOCK).plusSeconds(1);

        failed(user, MAX_ATTEMPTS - 1, later);
        assertThat(user.isBlocked(later)).isFalse();
        failed(user, 1, later);

        assertThat(user.isBlocked(later)).isTrue();
        assertThat(user.getBlockedUntil()).isEqualTo(later.plus(BLOCK));
    }

    @Test
    void aSuccessfulLoginShouldResetTheCount() {
        var user = failed(operator(), MAX_ATTEMPTS - 1, NOW);

        var changed = user.registerSuccessfulLogin();

        assertThat(changed).isTrue();
        assertThat(user.getFailedLoginAttempts()).isZero();
        failed(user, MAX_ATTEMPTS - 1, NOW);
        assertThat(user.isBlocked(NOW)).isFalse();
    }

    @Test
    void aSuccessfulLoginWithNothingToResetShouldChangeNothing() {
        assertThat(operator().registerSuccessfulLogin()).isFalse();
    }

    @Test
    void aSuccessfulLoginAfterTheBlockEndedShouldClearIt() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);

        var changed = user.registerSuccessfulLogin();

        assertThat(changed).isTrue();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getBlockedUntil()).isNull();
    }

    @Test
    void shouldNotCountFailuresOfAnInactiveUser() {
        var user = operator();
        user.deactivate();

        var changed = user.registerFailedLogin(NOW, MAX_ATTEMPTS, BLOCK);

        assertThat(changed).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getBlockedUntil()).isNull();
    }

    @Test
    void anInactiveUserCannotAuthenticateWhetherOrNotBlocked() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);
        user.deactivate();

        assertThat(user.canAuthenticate(NOW.plus(BLOCK).plusSeconds(1))).isFalse();
    }

    @Test
    void unblockShouldClearTheBlockAndTheCountRightAway() {
        var user = failed(operator(), MAX_ATTEMPTS, NOW);

        user.unblock();

        assertThat(user.isBlocked(NOW)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getBlockedUntil()).isNull();
        assertThat(user.canAuthenticate(NOW)).isTrue();
    }

    @Test
    void shouldHonorADifferentLimitAndDuration() {
        var user = operator();

        user.registerFailedLogin(NOW, 1, Duration.ofHours(1));

        assertThat(user.getBlockedUntil()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void shouldRefuseAnInvalidLimitOrDuration() {
        var user = operator();

        assertThatThrownBy(() -> user.registerFailedLogin(NOW, 0, BLOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.registerFailedLogin(NOW, MAX_ATTEMPTS, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.registerFailedLogin(NOW, MAX_ATTEMPTS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.registerFailedLogin(null, MAX_ATTEMPTS, BLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReconstituteTheLockoutState() {
        var user = User.reconstitute(7L, "maria", HASH, Role.OPERATOR, true, 3, NOW.plus(BLOCK));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getBlockedUntil()).isEqualTo(NOW.plus(BLOCK));
        assertThat(user.isBlocked(NOW)).isTrue();
    }

    @Test
    void theLockoutStateShouldNotChangeTheOtherData() {
        var user = failed(User.create("maria", HASH, Role.ADMIN), MAX_ATTEMPTS, NOW);

        assertThat(user.getLogin()).isEqualTo("maria");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
        assertThat(user.isActive()).isTrue();
    }

    // unique login (RF09)

    @Test
    void shouldAcceptALoginThatDoesNotExistYet() {
        User.validateUniqueLogin("maria", false);
    }

    @Test
    void shouldRejectALoginThatAlreadyExistsSayingWhichOneInTheNormalizedForm() {
        assertThatThrownBy(() -> User.validateUniqueLogin("  Maria ", true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já existe um usuário")
                .hasMessageContaining("maria");
    }
}
