package com.riverfount.booksmanager.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

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
        assertThat(operator().canAuthenticate()).isTrue();
    }

    @Test
    void deactivatedUserCannotAuthenticate() {
        var user = operator();

        user.deactivate();

        assertThat(user.isActive()).isFalse();
        assertThat(user.canAuthenticate()).isFalse();
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
}
