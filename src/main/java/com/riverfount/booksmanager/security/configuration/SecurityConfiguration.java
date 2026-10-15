package com.riverfount.booksmanager.security.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The security chain (RF08, RNF08): stateless, with the JWT bearer token as
 * the only way in. No session is created, so CSRF protection (which guards
 * session cookies) is off. Everything needs a valid token except the routes
 * below; the CORS filter runs before this chain (see CorsFilterConfiguration)
 * so the browser's preflight request, which has no token, is answered first.
 *
 * <p>Public routes: the authentication routes (login and refresh come with
 * issues #103 and #104), the health check (RNF23) and the OpenAPI
 * documentation with the Swagger UI (decided public on 15/10/2026).
 */
@Configuration
public class SecurityConfiguration {

    private static final String[] PUBLIC_ROUTES = {
            "/api/v1/auth/**",
            "/actuator/health", "/actuator/health/**",
            "/swagger-ui.html", "/swagger-ui/**",
            "/v3/api-docs", "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAuthenticationEntryPoint entryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler, JwtAuthenticationConverter converter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * Reads the bearer token from the Authorization header, except on the
     * authentication routes. There the header is ignored: a client that logs in
     * again may still send its old, expired access token, and an invalid token
     * would otherwise be answered with 401 even on a public route.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        var authRoutes = PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**");
        var header = new DefaultBearerTokenResolver();
        return request -> authRoutes.matches(request) ? null : header.resolve(request);
    }

    /** The profile travels in the {@code role} claim and becomes the {@code ROLE_*} authority. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
