package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminUseCase;
import com.riverfount.booksmanager.user.application.port.in.InitialAdminResult;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the system user use cases: checks what the domain needs,
 * hashes the password through the port and persists. Never logs a password.
 */
@Service
@Transactional
class UserService implements CreateInitialAdminUseCase, AuthenticateUserUseCase {

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
