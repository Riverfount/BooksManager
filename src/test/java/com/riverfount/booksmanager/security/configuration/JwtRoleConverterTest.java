package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The profile travels in the {@code role} claim and becomes the
 * {@code ROLE_*} authority that {@code hasRole(...)} checks (RF08, RNF08).
 */
class JwtRoleConverterTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    /**
     * Spring Security also adds a FACTOR_BEARER authority to a bearer token
     * (it records how the user authenticated); only the ROLE_* ones come from
     * the token's claims.
     */
    private static List<String> roles(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("FACTOR_"))
                .toList();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(900), Map.of("alg", "HS256"), claims);
    }

    @Test
    void shouldTurnTheRoleClaimIntoARoleAuthority() {
        var authentication = configuration.jwtAuthenticationConverter()
                .convert(jwt(Map.of("sub", "7", "role", "ADMIN")));

        assertThat(roles(authentication)).containsExactly("ROLE_ADMIN");
        assertThat(authentication.getName()).isEqualTo("7");
    }

    @Test
    void shouldHandleTheOperatorRole() {
        var authentication = configuration.jwtAuthenticationConverter()
                .convert(jwt(Map.of("sub", "7", "role", "OPERATOR")));

        assertThat(roles(authentication)).containsExactly("ROLE_OPERATOR");
    }

    @Test
    void shouldGiveNoAuthorityWithoutTheRoleClaim() {
        var authentication = configuration.jwtAuthenticationConverter().convert(jwt(Map.of("sub", "7")));

        assertThat(roles(authentication)).isEmpty();
    }

    @Test
    void shouldNotTurnTheScopeClaimIntoAuthorities() {
        var authentication = configuration.jwtAuthenticationConverter()
                .convert(jwt(Map.of("sub", "7", "role", "OPERATOR", "scope", "admin")));

        assertThat(roles(authentication)).containsExactly("ROLE_OPERATOR");
    }
}
