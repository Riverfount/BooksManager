package com.riverfount.booksmanager.audit.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CallerActorTest {

    private final CallerActor callerActor = new CallerActor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithSubject(String subject) {
        var jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), java.util.Map.of("alg", "HS256"),
                java.util.Map.of("sub", subject, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
    }

    @Test
    void shouldTakeTheIdFromTheSubjectOfTheToken() {
        authenticateWithSubject("7");

        assertThat(callerActor.id()).contains(7L);
    }

    @Test
    void shouldBeEmptyWhenNobodyIsAuthenticated() {
        assertThat(callerActor.id()).isEmpty();
    }

    @Test
    void shouldBeEmptyForAnAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(callerActor.id()).isEmpty();
    }

    @Test
    void shouldBeEmptyWhenTheSubjectIsNotAnId() {
        authenticateWithSubject("not-a-number");

        assertThat(callerActor.id()).isEmpty();
    }

    @Test
    void shouldBeEmptyForAnAuthenticationThatIsNotAToken() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", "n/a", List.of()));

        assertThat(callerActor.id()).isEmpty();
    }
}
