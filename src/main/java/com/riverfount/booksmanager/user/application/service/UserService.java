package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminUseCase;
import com.riverfount.booksmanager.user.application.port.in.InitialAdminResult;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.PasswordPolicy;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the system user use cases: checks what the domain needs,
 * hashes the password through the port and persists. Never logs a password.
 */
@Service
@Transactional
class UserService implements CreateInitialAdminUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
