package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.shared.application.port.out.RateLimitPolicy;
import com.riverfount.booksmanager.shared.application.port.out.RateLimitResult;
import com.riverfount.booksmanager.shared.application.port.out.RateLimiter;
import com.riverfount.booksmanager.shared.domain.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Limits the requests of a reader to the routes of the reader (Phase 6, RNF31): counted per reader of the token, only
 * on {@code /api/v1/me/**}, only for a caller the chain already accepted, and a refusal is the 429 of the API.
 */
class ReaderRateLimitFilterTest {

    private static final RateLimitPolicy POLICY = new RateLimitPolicy("reader-routes", 120, Duration.ofMinutes(1));

    private RateLimiter limiter;
    private HandlerExceptionResolver resolver;
    private FilterChain chain;
    private ReaderRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        limiter = mock(RateLimiter.class);
        resolver = mock(HandlerExceptionResolver.class);
        chain = mock(FilterChain.class);
        filter = new ReaderRateLimitFilter(limiter, POLICY, resolver);
        given(limiter.tryAcquire(any(), any())).willReturn(RateLimitResult.allow());
    }

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String subject, Object readerIdClaim) {
        var jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(subject)
                .claim("role", "READER");
        if (readerIdClaim != null) {
            jwt.claim("readerId", readerIdClaim);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt.build(), java.util.List.of(new SimpleGrantedAuthority("ROLE_READER"))));
    }

    private void run(String uri) throws Exception {
        var request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void shouldCountTheRequestOfTheReaderOfTheTokenAndLetItGoOn() throws Exception {
        authenticate("5", 42L);

        run("/api/v1/me/books");

        then(limiter).should().tryAcquire(POLICY, "reader:42");
        then(chain).should().doFilter(any(), any());
    }

    @Test
    void shouldCountEveryRouteOfTheReader() throws Exception {
        authenticate("5", 42L);

        run("/api/v1/me");
        run("/api/v1/me/reservations/9/cancel");

        then(limiter).should(org.mockito.Mockito.times(2)).tryAcquire(POLICY, "reader:42");
    }

    @Test
    void shouldNotCountTheOtherRoutesNorALookalikePrefix() throws Exception {
        authenticate("5", 42L);

        run("/api/v1/books");
        run("/api/v1/measure");
        run("/api/v1/auth/login");

        then(limiter).should(never()).tryAcquire(any(), any());
        then(chain).should(org.mockito.Mockito.times(3)).doFilter(any(), any());
    }

    @Test
    void shouldCountByTheSubjectWhenTheTokenHasNoReader() throws Exception {
        authenticate("5", null);

        run("/api/v1/me/books");

        then(limiter).should().tryAcquire(POLICY, "user:5");
    }

    @Test
    void shouldCountByTheSubjectWhenTheReaderClaimIsNotAWholeNumber() throws Exception {
        authenticate("5", "abc");

        run("/api/v1/me/books");

        then(limiter).should().tryAcquire(POLICY, "user:5");
    }

    @Test
    void shouldLeaveTheCallerWithoutATokenToTheChain() throws Exception {
        run("/api/v1/me/books");

        then(limiter).should(never()).tryAcquire(any(), any());
        then(chain).should().doFilter(any(), any());
    }

    @Test
    void shouldStopTheRequestAndAnswer429WhenOverTheLimit() throws Exception {
        authenticate("5", 42L);
        given(limiter.tryAcquire(eq(POLICY), any())).willReturn(RateLimitResult.refuse(Duration.ofSeconds(17)));

        run("/api/v1/me/books");

        var captor = ArgumentCaptor.forClass(Exception.class);
        then(resolver).should().resolveException(any(), any(), eq(null), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RateLimitExceededException.class);
        assertThat(((RateLimitExceededException) captor.getValue()).getRetryAfter()).isEqualTo(Duration.ofSeconds(17));
        then(chain).should(never()).doFilter(any(), any());
    }
}
