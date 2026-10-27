package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminUseCase;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.DeactivateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.DeactivateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.ListUsersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListUsersUseCase;
import com.riverfount.booksmanager.user.application.port.in.ResetUserPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetUserPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.in.UnblockUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.InitialAdminResult;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.UserFilter;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the system user use cases: checks what the domain needs,
 * hashes the password through the port and persists. Never logs a password. Each sensitive operation, and every
 * login, is reported to the audit trail (RF27) in the same transaction, after the domain accepted it; what is
 * reported never includes a password, a hash or a token.
 */
@Service
@Transactional
class UserService implements CreateInitialAdminUseCase, AuthenticateUserUseCase, CreateUserUseCase,
        GetUserUseCase, ListUsersUseCase, UpdateUserUseCase, UnblockUserUseCase, DeactivateUserUseCase,
        ResetUserPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final LoginLockoutProperties lockout;
    private final RevokeRefreshTokensUseCase refreshTokens;
    private final RecordAuditEventUseCase audit;
    private volatile String fakeHash;

    UserService(UserRepository userRepository, PasswordHasher passwordHasher, Clock clock,
            LoginLockoutProperties lockout, RevokeRefreshTokensUseCase refreshTokens, RecordAuditEventUseCase audit) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.lockout = lockout;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Override
    public InitialAdminResult createInitialAdmin(CreateInitialAdminCommand command) {
        if (userRepository.count() > 0) {
            return InitialAdminResult.ALREADY_HAS_USERS;
        }
        if (isBlank(command.login()) || isBlank(command.password())) {
            return InitialAdminResult.NOT_CONFIGURED;
        }
        PasswordPolicy.validate(command.password());
        var admin = userRepository.save(User.create(command.login(), passwordHasher.hash(command.password()), Role.ADMIN));
        // nobody is authenticated at startup, so the entry has no actor
        audit.record(new AuditEvent(AuditAction.USER_CREATED, AuditTarget.USER, admin.getId(),
                "administrador inicial criado na subida"));
        return InitialAdminResult.CREATED;
    }

    /**
     * The password policy is checked first (cheap, and the answer says which rules
     * are missing), then the password is hashed and the user built, which validates
     * the login and the profile; the login must be new. The unique constraint of the
     * database is the last defense against two simultaneous creations.
     */
    @Override
    public User create(CreateUserCommand command) {
        PasswordPolicy.validate(command.password());
        var user = User.create(command.login(), passwordHasher.hash(command.password()), command.role(),
                command.displayName());
        User.validateUniqueLogin(user.getLogin(), userRepository.existsByLogin(user.getLogin()));
        var saved = userRepository.save(user);
        audit.record(new AuditEvent(AuditAction.USER_CREATED, AuditTarget.USER, saved.getId(),
                "login: " + saved.getLogin() + ", perfil: " + saved.getRole()));
        return saved;
    }

    /**
     * Not rolled back when the credentials are refused: the failed attempt has
     * to be recorded (login lockout, RF08), and the exception that reports the
     * refusal would otherwise undo it. This only holds if the caller's own
     * transaction, which this one joins, does not roll back on that exception
     * either (see LoginService.login).
     */
    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthenticatedUser authenticate(AuthenticateUserCommand command) {
        var now = Instant.now(clock);
        // the row is locked until the end of the transaction, so parallel attempts on the same user are counted one by one
        var user = userRepository.findByLoginForAuthentication(command.login()).orElse(null);
        // The password is always checked, even when the login does not exist, the
        // user is inactive or blocked, against a fake hash in the first case: all
        // the failures then cost the same time, which would otherwise reveal which
        // logins exist. The password and the reason are never reported.
        var storedHash = user != null ? user.getPasswordHash() : fakeHash();
        var passwordMatches = passwordHasher.matches(command.password(), storedHash);
        if (user != null && passwordMatches && user.canAuthenticate(now)) {
            if (user.registerSuccessfulLogin()) {
                userRepository.save(user);
            }
            audit.recordAs(user.getId(), new AuditEvent(AuditAction.LOGIN_SUCCESS, AuditTarget.USER, user.getId(), null));
            return new AuthenticatedUser(user.getId(), user.getRole(), user.getReaderId());
        }
        var changed = user != null && !passwordMatches
                && user.registerFailedLogin(now, lockout.maxFailedAttempts(), lockout.blockDuration());
        if (changed) {
            userRepository.save(user);
        }
        auditRefusedLogin(user, passwordMatches, changed, now);
        throw new InvalidCredentialsException();
    }

    /**
     * Every refusal is reported, whatever the reason, so the trail does not tell apart (by what is recorded or by
     * how long it takes) a login that exists from one that does not. The login that was typed is never stored: a
     * password typed in the wrong field would end up in the trail. A refusal that blocked the user is reported twice,
     * the failure and the lockout.
     */
    private void auditRefusedLogin(User user, boolean passwordMatches, boolean changed, Instant now) {
        if (user == null) {
            audit.recordAs(null, new AuditEvent(AuditAction.LOGIN_FAILURE, null, null, "login desconhecido"));
            return;
        }
        var reason = !passwordMatches ? "senha incorreta" : !user.isActive() ? "usuário inativo" : "usuário bloqueado";
        audit.recordAs(user.getId(), new AuditEvent(AuditAction.LOGIN_FAILURE, AuditTarget.USER, user.getId(), reason));
        if (changed && user.isBlocked(now)) {
            audit.recordAs(user.getId(), new AuditEvent(AuditAction.LOGIN_LOCKOUT, AuditTarget.USER, user.getId(),
                    "bloqueado até " + user.getBlockedUntil().truncatedTo(ChronoUnit.SECONDS) + " após " + lockout.maxFailedAttempts()
                            + " tentativas erradas"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> list(ListUsersQuery query) {
        return userRepository.findAll(new UserFilter(query.login(), query.active(), query.role()), query.page());
    }

    /**
     * The active administrators are locked first (in id order, so two edits cannot
     * wait for each other), then the user: with the locks held, the count of
     * "other administrators" cannot change under this edit.
     */
    @Override
    public User update(UpdateUserCommand command) {
        var administrators = userRepository.findActiveByRoleForUpdate(Role.ADMIN);
        var user = loadForUpdate(command.id());
        var changes = new ArrayList<String>();
        if (command.role() != null) {
            var previous = user.getRole();
            var others = administrators.stream().filter(admin -> !admin.getId().equals(user.getId())).count();
            user.changeRoleKeepingAnAdministrator(command.role(), others);
            if (previous != user.getRole()) {
                changes.add("perfil: " + previous + " → " + user.getRole());
            }
        }
        if (command.displayName() != null) {
            var previous = user.getDisplayName();
            user.changeDisplayName(command.displayName());
            if (!Objects.equals(previous, user.getDisplayName())) {
                changes.add("nome de exibição alterado");
            }
        }
        var saved = userRepository.save(user);
        if (!changes.isEmpty()) {
            audit.record(new AuditEvent(AuditAction.USER_UPDATED, AuditTarget.USER, saved.getId(),
                    String.join("; ", changes)));
        }
        return saved;
    }

    /**
     * Same locks as the profile edit (the active administrators, then the user), so
     * two administrators deactivating each other at the same time are handled one
     * after the other and the system keeps an active administrator. The refresh
     * tokens are revoked in the same transaction, even when the user was already
     * inactive (so a token left behind is revoked too); if the revocation fails,
     * the deactivation is rolled back with it.
     */
    @Override
    public void deactivate(DeactivateUserCommand command) {
        var administrators = userRepository.findActiveByRoleForUpdate(Role.ADMIN);
        var user = loadForUpdate(command.id());
        if (user.getId().equals(command.actingUserId())) {
            throw new BusinessRuleException("não é possível inativar o próprio usuário");
        }
        var others = administrators.stream().filter(admin -> !admin.getId().equals(user.getId())).count();
        if (user.deactivateKeepingAnAdministrator(others)) {
            userRepository.save(user);
            audit.record(new AuditEvent(AuditAction.USER_DEACTIVATED, AuditTarget.USER, user.getId(), null));
        }
        refreshTokens.revokeAllOf(user.getId());
    }

    /**
     * The policy is checked first (cheap, and the answer says which rules are missing),
     * then the user is loaded locked, so the new hash does not overwrite a change made
     * at the same time. The refresh tokens are revoked in the same transaction: if the
     * revocation fails, the new password is rolled back with it.
     */
    @Override
    public void resetPassword(ResetUserPasswordCommand command) {
        PasswordPolicy.validate(command.password());
        var user = loadForUpdate(command.id());
        user.changePasswordHash(passwordHasher.hash(command.password()));
        userRepository.save(user);
        refreshTokens.revokeAllOf(user.getId());
        // only the fact is reported: never the new password nor its hash
        audit.record(new AuditEvent(AuditAction.USER_PASSWORD_RESET, AuditTarget.USER, user.getId(), null));
    }

    @Override
    public void unblock(Long id) {
        var user = loadForUpdate(id);
        if (user.unblock()) {
            userRepository.save(user);
            audit.record(new AuditEvent(AuditAction.USER_UNBLOCKED, AuditTarget.USER, user.getId(), null));
        }
    }

    private User loadForUpdate(Long id) {
        return userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("usuário não encontrado: " + id));
    }

    /**
     * A hash made by the same hasher (so with the same cost as the real ones)
     * of a random password nobody knows, created on first use and then kept.
     */
    private String fakeHash() {
        var hash = fakeHash;
        if (hash == null) {
            synchronized (this) {
                hash = fakeHash;
                if (hash == null) {
                    hash = passwordHasher.hash(UUID.randomUUID().toString());
                    fakeHash = hash;
                }
            }
        }
        return hash;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
