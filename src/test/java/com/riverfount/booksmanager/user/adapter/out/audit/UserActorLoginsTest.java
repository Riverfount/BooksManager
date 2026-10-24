package com.riverfount.booksmanager.user.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserActorLoginsTest {

    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserActorLogins actorLogins;

    @Test
    void shouldGiveTheLoginOfTheUser() {
        given(userRepository.findById(7L)).willReturn(Optional.of(User.reconstitute(7L, "maria", HASH, Role.OPERATOR,
                true, 0, null, null)));

        assertThat(actorLogins.loginOf(7L)).contains("maria");
    }

    @Test
    void shouldStillGiveTheLoginOfAnInactiveUser() {
        given(userRepository.findById(7L)).willReturn(Optional.of(User.reconstitute(7L, "maria", HASH, Role.OPERATOR,
                false, 0, null, null)));

        assertThat(actorLogins.loginOf(7L)).contains("maria");
    }

    @Test
    void shouldBeEmptyForAnUnknownUser() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThat(actorLogins.loginOf(99L)).isEmpty();
    }
}
