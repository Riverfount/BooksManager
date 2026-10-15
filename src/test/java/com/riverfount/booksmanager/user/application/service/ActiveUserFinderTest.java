package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Finding an active user for a token renewal (RF08): who the user is right now, if still active. */
class ActiveUserFinderTest {

    private static final String HASH = "{hash}x";

    private InMemoryUserRepository repository;
    private ActiveUserFinder finder;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        finder = new ActiveUserFinder(repository);
    }

    private User save(String login, Role role) {
        return repository.save(User.create(login, HASH, role));
    }

    @Test
    void shouldFindAnActiveUserByIdWithItsCurrentRole() {
        var saved = save("maria", Role.OPERATOR);

        assertThat(finder.findActiveUser(saved.getId())).contains(new AuthenticatedUser(saved.getId(), Role.OPERATOR));

        var user = repository.findById(saved.getId()).orElseThrow();
        user.changeRole(Role.ADMIN);
        repository.save(user);
        assertThat(finder.findActiveUser(saved.getId())).contains(new AuthenticatedUser(saved.getId(), Role.ADMIN));
    }

    @Test
    void shouldNotFindAnInactiveOrUnknownUser() {
        var saved = save("maria", Role.OPERATOR);
        var user = repository.findById(saved.getId()).orElseThrow();
        user.deactivate();
        repository.save(user);

        assertThat(finder.findActiveUser(saved.getId())).isEmpty();
        assertThat(finder.findActiveUser(99_999L)).isEmpty();
        assertThat(finder.findActiveUser(null)).isEmpty();
    }

    @Test
    void aUserBlockedByTheLockoutIsStillFoundSinceTheBlockOnlyGuardsThePassword() {
        var saved = save("maria", Role.OPERATOR);
        var user = repository.findById(saved.getId()).orElseThrow();
        user.registerFailedLogin(Instant.parse("2026-10-15T12:00:00Z"), 1, java.time.Duration.ofMinutes(15));
        repository.save(user);

        assertThat(finder.findActiveUser(saved.getId())).isPresent();
    }
}
