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
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
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
    private volatile String fakeHash;

    UserService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
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

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(AuthenticateUserCommand command) {
        var user = userRepository.findByLogin(command.login()).orElse(null);
        // The password is always checked, even when the login does not exist or
        // the user is inactive, against a fake hash in the first case: all the
        // failures then cost the same time, which would otherwise reveal which
        // logins exist. The password and the reason are never reported.
        var storedHash = user != null ? user.getPasswordHash() : fakeHash();
        var passwordMatches = passwordHasher.matches(command.password(), storedHash);
        if (user == null || !passwordMatches || !user.canAuthenticate()) {
            throw new InvalidCredentialsException();
        }
        return new AuthenticatedUser(user.getId(), user.getRole());
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
