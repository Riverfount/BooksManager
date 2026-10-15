package com.riverfount.booksmanager.security.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers 401 (missing, expired or invalid token) as ProblemDetail (RNF04).
 * It hands the exception to the MVC exception resolver, so the body comes
 * from the same {@code GlobalExceptionHandler} as every other error and
 * never carries the reason the token was refused. The Bearer challenge
 * (RFC 6750) tells the client which scheme to use.
 */
@Component
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    ProblemDetailAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) {
        response.setHeader("WWW-Authenticate", "Bearer");
        resolver.resolveException(request, response, null, authException);
    }
}
