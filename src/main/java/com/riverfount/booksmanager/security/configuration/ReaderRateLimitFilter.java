package com.riverfount.booksmanager.security.configuration;

import com.riverfount.booksmanager.shared.adapter.in.web.CurrentReader;
import com.riverfount.booksmanager.shared.application.port.out.RateLimitPolicy;
import com.riverfount.booksmanager.shared.application.port.out.RateLimiter;
import com.riverfount.booksmanager.shared.domain.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Limits the requests of a reader to the routes of the reader (Phase 6, RNF31): {@code /api/v1/me/**}, counted per
 * reader of the token. It runs after the authorization of the chain, so only a caller the chain already accepted for
 * those routes is counted, and a refusal is the same 429 of the API (ProblemDetail, with {@code Retry-After}), made by
 * the same exception handler. It is not a bean on purpose: a filter bean would also be registered by the servlet
 * container, outside the chain, where nobody is authenticated yet.
 *
 * <p>The count is by the {@code readerId} of the token; a token without a valid one (which the routes refuse with 403
 * anyway) is counted by its subject, so it does not run unlimited either. The first barrier is the Nginx, by address;
 * this one is by reader, which the Nginx cannot see.
 */
final class ReaderRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher READER_ROUTES =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/me/**");
    private static final RequestMatcher READER_ROOT =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/me");

    private final RateLimiter limiter;
    private final RateLimitPolicy policy;
    private final HandlerExceptionResolver resolver;

    ReaderRateLimitFilter(RateLimiter limiter, RateLimitPolicy policy, HandlerExceptionResolver resolver) {
        this.limiter = limiter;
        this.policy = policy;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(READER_ROUTES.matches(request) || READER_ROOT.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt token) {
            var result = limiter.tryAcquire(policy, keyOf(token));
            if (!result.allowed()) {
                resolver.resolveException(request, response, null, new RateLimitExceededException(result.retryAfter()));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String keyOf(Jwt token) {
        var claim = token.getClaim(CurrentReader.CLAIM);
        if ((claim instanceof Long || claim instanceof Integer) && ((Number) claim).longValue() > 0) {
            return "reader:" + ((Number) claim).longValue();
        }
        return "user:" + token.getSubject();
    }
}
