package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminUseCase;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.ListUsersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListUsersUseCase;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the system user use cases: checks what the domain needs,
 * hashes the password through the port and persists. Never logs a password.
 */
@Service
@Transactional
class UserService implements CreateInitialAdminUseCase, AuthenticateUserUseCase, CreateUserUseCase,
        GetUserUseCase, ListUsersUseCase, UpdateUserUseCase, UnblockUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final LoginLockoutProperties lockout;
    private volatile String fakeHash;

    UserService(UserRepository userRepository, PasswordHasher passwordHasher, Clock clock,
            LoginLockoutProperties lockout) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.lockout = lockout;
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
        userRepository.save(User.create(command.login(), passwordHasher.hash(command.password()), Role.ADMIN));
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
        var user = User.create(command.login(), passwordHasher.hash(command.password()), command.role());
        User.validateUniqueLogin(user.getLogin(), userRepository.existsByLogin(user.getLogin()));
        return userRepository.save(user);
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
            return new AuthenticatedUser(user.getId(), user.getRole());
        }
        if (user != null && !passwordMatches
                && user.registerFailedLogin(now, lockout.maxFailedAttempts(), lockout.blockDuration())) {
            userRepository.save(user);
        }
        throw new InvalidCredentialsException();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> list(ListUsersQuery query) {
        return userRepository.findAll(new UserFilter(query.login(), query.active()), query.page());
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
        if (command.role() != null) {
            var others = administrators.stream().filter(admin -> !admin.getId().equals(user.getId())).count();
            user.changeRoleKeepingAnAdministrator(command.role(), others);
        }
        return userRepository.save(user);
    }

    @Override
    public void unblock(Long id) {
        var user = loadForUpdate(id);
        if (user.unblock()) {
            userRepository.save(user);
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
