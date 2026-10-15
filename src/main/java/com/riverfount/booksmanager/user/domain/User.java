package com.riverfount.booksmanager.user.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.Locale;

/**
 * System user, who operates the library (RF08, RF09), separate from the
 * reader. The password only exists here as a hash that arrives ready: the
 * domain does not know BCrypt (the encoder is an outbound port), so the hash
 * is never produced nor checked here (RNF07). Users are deactivated, never
 * physically deleted.
 *
 * <p>The login is stored in lowercase, so it is not case sensitive; lookups
 * must use {@link #normalizeLogin(String)}.
 */
public class User {

    public static final int MAX_LOGIN_LENGTH = 50;
    public static final int MAX_PASSWORD_HASH_LENGTH = 100;

    private final Long id;
    private final String login;
    private String passwordHash;
    private Role role;
    private boolean active;

    private User(Long id, String login, String passwordHash, Role role, boolean active) {
        this.id = id;
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public static User create(String login, String passwordHash, Role role) {
        validatePasswordHash(passwordHash);
        validateRole(role);
        return new User(null, validLogin(login), passwordHash, role, true);
    }

    public static User reconstitute(Long id, String login, String passwordHash, Role role, boolean active) {
        return new User(id, login, passwordHash, role, active);
    }

    /**
     * Login normalization: no surrounding spaces, lowercase. A null login
     * becomes empty. Public so that lookups (authentication, uniqueness) use
     * the same rule as the registration.
     */
    public static String normalizeLogin(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    public void changeRole(Role newRole) {
        validateRole(newRole);
        this.role = newRole;
    }

    public void changePasswordHash(String newPasswordHash) {
        validatePasswordHash(newPasswordHash);
        this.passwordHash = newPasswordHash;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Whether the user may be authenticated (RF08): only active users. */
    public boolean canAuthenticate() {
        return active;
    }

    private static String validLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new BusinessRuleException("o login é obrigatório");
        }
        if (login.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessRuleException("o login não pode ter espaços");
        }
        if (login.length() > MAX_LOGIN_LENGTH) {
            throw new BusinessRuleException("o login deve ter até " + MAX_LOGIN_LENGTH + " caracteres");
        }
        return normalizeLogin(login);
    }

    private static void validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new BusinessRuleException("o hash da senha é obrigatório");
        }
        if (passwordHash.length() > MAX_PASSWORD_HASH_LENGTH) {
            throw new BusinessRuleException(
                    "o hash da senha deve ter até " + MAX_PASSWORD_HASH_LENGTH + " caracteres");
        }
    }

    private static void validateRole(Role role) {
        if (role == null) {
            throw new BusinessRuleException("o perfil do usuário é obrigatório");
        }
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
