package com.riverfount.booksmanager.audit.adapter.out.security;

import com.riverfount.booksmanager.audit.application.port.out.CurrentActor;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The caller of the current request, read from the security context: the access token carries the user id as its
 * subject. Empty when nobody is authenticated (the administrator created at startup) or when the subject is not an
 * id, so the trail never fails an operation for lack of an actor.
 */
@Component
class CallerActor implements CurrentActor {

    @Override
    public Optional<Long> id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(token.getToken().getSubject()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
