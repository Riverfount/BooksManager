package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.application.port.in.FindActiveUserUseCase;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the active user that the security module needs when it renews a token
 * (RF08). It is a class of its own, apart from {@link UserService}, on purpose:
 * the user service calls the security module (to revoke the tokens of an
 * inactivated user) and the security module calls this one, so keeping them
 * together would make the two depend on each other and the application would
 * not start.
 */
@Service
class ActiveUserFinder implements FindActiveUserUseCase {

    private final UserRepository userRepository;

    ActiveUserFinder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findActiveUser(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id)
                .filter(User::isActive)
                .map(user -> new AuthenticatedUser(user.getId(), user.getRole()));
    }
}
