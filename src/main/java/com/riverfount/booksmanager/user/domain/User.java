package com.riverfount.booksmanager.user.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
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
 *
 * <p>Login lockout (RF08): wrong passwords are counted, and when the limit is
 * reached the user is blocked until a given instant. The block ends by itself
 * when that instant passes (so nobody can be locked out forever by someone
 * guessing passwords), and a new round of attempts starts. Failures of an
 * inactive or already blocked user are not counted. The limit and the
 * duration come by parameter, and so does the current instant: the domain
 * never reads the clock.
 *
 * <p>A user with the {@link Role#READER} profile is the access of one reader (Phase 6): it is tied to the reader by id,
 * is created only by {@link #createReaderAccess} (when the reader accepts an invitation) and never changes profile.
 *
 * <p>The display name is what a screen shows for who is logged in. It is optional: blank
 * means none (the screens fall back to the login), and it is kept without surrounding spaces.
 */
public class User {

    public static final int MAX_LOGIN_LENGTH = 50;
    public static final int MAX_PASSWORD_HASH_LENGTH = 100;
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final Long id;
    private final String login;
    private String passwordHash;
    private Role role;
    private boolean active;
    private int failedLoginAttempts;
    private Instant blockedUntil;
    private String displayName;
    private final Long readerId;

    private User(Long id, String login, String passwordHash, Role role, boolean active, int failedLoginAttempts,
            Instant blockedUntil, String displayName, Long readerId) {
        this.id = id;
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.failedLoginAttempts = failedLoginAttempts;
        this.blockedUntil = blockedUntil;
        this.displayName = displayName;
        this.readerId = readerId;
    }

    public static User create(String login, String passwordHash, Role role) {
        return create(login, passwordHash, role, null);
    }

    public static User create(String login, String passwordHash, Role role, String displayName) {
        validatePasswordHash(passwordHash);
        validateRole(role);
        if (role == Role.READER) {
            throw new BusinessRuleException("o perfil de leitor só é concedido por convite");
        }
        return new User(null, validLogin(login), passwordHash, role, true, 0, null, validDisplayName(displayName),
                null);
    }

    /** The access of a reader (Phase 6): the READER profile, tied to the reader, created when the invitation is accepted. */
    public static User createReaderAccess(String login, String passwordHash, Long readerId) {
        validatePasswordHash(passwordHash);
        if (readerId == null) {
            throw new BusinessRuleException("o leitor do acesso é obrigatório");
        }
        return new User(null, validLogin(login), passwordHash, Role.READER, true, 0, null, null, readerId);
    }

    /** A persisted user with no failed attempts and not blocked. */
    public static User reconstitute(Long id, String login, String passwordHash, Role role, boolean active) {
        return new User(id, login, passwordHash, role, active, 0, null, null, null);
    }

    public static User reconstitute(Long id, String login, String passwordHash, Role role, boolean active,
            int failedLoginAttempts, Instant blockedUntil) {
        return new User(id, login, passwordHash, role, active, failedLoginAttempts, blockedUntil, null, null);
    }

    public static User reconstitute(Long id, String login, String passwordHash, Role role, boolean active,
            int failedLoginAttempts, Instant blockedUntil, String displayName) {
        return new User(id, login, passwordHash, role, active, failedLoginAttempts, blockedUntil, displayName, null);
    }

    public static User reconstitute(Long id, String login, String passwordHash, Role role, boolean active,
            int failedLoginAttempts, Instant blockedUntil, String displayName, Long readerId) {
        return new User(id, login, passwordHash, role, active, failedLoginAttempts, blockedUntil, displayName,
                readerId);
    }

    /**
     * Login normalization: no surrounding spaces, lowercase. A null login
     * becomes empty. Public so that lookups (authentication, uniqueness) use
     * the same rule as the registration.
     */
    public static String normalizeLogin(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Unique login rule (RF09). Like {@code Reader.validateUniqueDocument}, the
     * application service looks up whether the login exists and the domain decides.
     */
    public static void validateUniqueLogin(String login, boolean alreadyExistsWithThisLogin) {
        if (alreadyExistsWithThisLogin) {
            throw new BusinessRuleException("já existe um usuário com o login '" + normalizeLogin(login) + "'");
        }
    }

    public void changeRole(Role newRole) {
        validateRoleChange(newRole);
        this.role = newRole;
    }

    /**
     * Changes the profile without leaving the system with no administrator (RF09):
     * an active administrator cannot lose the profile when no other active one
     * remains. The caller counts the others; a user blocked by the login lockout
     * still counts, since the block ends by itself. Keeping the same profile, or
     * demoting an inactive administrator (who counted for nothing), is always allowed.
     */
    public void changeRoleKeepingAnAdministrator(Role newRole, long otherActiveAdministrators) {
        validateRoleChange(newRole);
        if (active && role == Role.ADMIN && newRole != Role.ADMIN && otherActiveAdministrators < 1) {
            throw new BusinessRuleException(
                    "não é possível tirar o perfil de administrador do último administrador ativo");
        }
        this.role = newRole;
    }

    /** Changes the display name; a blank one clears it. A refused name leaves the current one untouched. */
    public void changeDisplayName(String newDisplayName) {
        this.displayName = validDisplayName(newDisplayName);
    }

    public void changePasswordHash(String newPasswordHash) {
        validatePasswordHash(newPasswordHash);
        this.passwordHash = newPasswordHash;
    }

    /**
     * Deactivates without leaving the system with no administrator (RF09): an active
     * administrator cannot be deactivated when no other active one remains. The caller
     * counts the others (a user blocked by the login lockout still counts, since the
     * block ends by itself). Deactivating a user who is already inactive changes nothing.
     *
     * @return whether the user changed and has to be saved
     */
    public boolean deactivateKeepingAnAdministrator(long otherActiveAdministrators) {
        if (!active) {
            return false;
        }
        if (role == Role.ADMIN && otherActiveAdministrators < 1) {
            throw new BusinessRuleException("não é possível inativar o último administrador ativo");
        }
        this.active = false;
        return true;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Whether the user may be authenticated (RF08): active and not blocked at the given instant. */
    public boolean canAuthenticate(Instant now) {
        return active && !isBlocked(now);
    }

    /** Whether the login lockout is in force at the given instant; it ends exactly at {@code blockedUntil}. */
    public boolean isBlocked(Instant now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    /**
     * Counts a wrong password. The attempt that reaches {@code maxAttempts}
     * blocks the user for {@code blockDuration}. Nothing is counted for an
     * inactive or currently blocked user (so the block is not extended); a
     * block that already ended is cleared first, which starts a new round.
     *
     * @return whether the user changed and has to be saved
     */
    public boolean registerFailedLogin(Instant now, int maxAttempts, Duration blockDuration) {
        if (now == null || maxAttempts < 1 || blockDuration == null || blockDuration.isZero()
                || blockDuration.isNegative()) {
            throw new IllegalArgumentException("o instante, o limite de tentativas e a duração do bloqueio são "
                    + "obrigatórios e devem ser positivos");
        }
        if (!active || isBlocked(now)) {
            return false;
        }
        if (blockedUntil != null) {
            this.failedLoginAttempts = 0;
            this.blockedUntil = null;
        }
        this.failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            this.blockedUntil = now.plus(blockDuration);
        }
        return true;
    }

    /**
     * A correct password, for a user who may authenticate: resets the count and
     * clears a block that already ended.
     *
     * @return whether the user changed and has to be saved
     */
    public boolean registerSuccessfulLogin() {
        if (failedLoginAttempts == 0 && blockedUntil == null) {
            return false;
        }
        this.failedLoginAttempts = 0;
        this.blockedUntil = null;
        return true;
    }

    /**
     * Lifts the lockout right away and resets the count (the manual unblock by an
     * administrator).
     *
     * @return whether the user changed and has to be saved
     */
    public boolean unblock() {
        if (failedLoginAttempts == 0 && blockedUntil == null) {
            return false;
        }
        this.failedLoginAttempts = 0;
        this.blockedUntil = null;
        return true;
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

    private static String validDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        var stripped = displayName.strip();
        if (stripped.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new BusinessRuleException("o nome de exibição deve ter até " + MAX_DISPLAY_NAME_LENGTH
                    + " caracteres");
        }
        return stripped;
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

    /** The READER profile is neither given to nor taken from a user: it belongs to the access of a reader. */
    private void validateRoleChange(Role newRole) {
        validateRole(newRole);
        if (newRole != role && (newRole == Role.READER || role == Role.READER)) {
            throw new BusinessRuleException("o perfil de leitor não pode ser atribuído nem removido de um usuário");
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

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    /** The reader this access belongs to, or null for a user of the system (administrator or operator). */
    public Long getReaderId() {
        return readerId;
    }

    /** The name screens show for this user, or null when there is none (they fall back to the login). */
    public String getDisplayName() {
        return displayName;
    }
}
