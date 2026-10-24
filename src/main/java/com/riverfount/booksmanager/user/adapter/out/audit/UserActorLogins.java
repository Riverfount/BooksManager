package com.riverfount.booksmanager.user.adapter.out.audit;

import com.riverfount.booksmanager.audit.application.port.out.ActorLogins;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The user module's answer to the audit's question "what is the login of this user" (RF27): the audit defines the
 * port in its own language and the user module implements it, so the audit never depends on the user module. An
 * inactive user still has a login, because the trail names who did something even after they were deactivated.
 */
@Component
class UserActorLogins implements ActorLogins {

    private final UserRepository userRepository;

    UserActorLogins(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> loginOf(Long userId) {
        return userRepository.findById(userId).map(User::getLogin);
    }
}
