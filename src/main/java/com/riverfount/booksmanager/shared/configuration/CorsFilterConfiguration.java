package com.riverfount.booksmanager.shared.configuration;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Restricts CORS to the front-end origin (RNF09). The filter runs before
 * the Spring Security chain: the browser's preflight request (OPTIONS)
 * carries no credentials, so it would otherwise be answered with 401
 * before any CORS header was added. Only the {@code /api/**} routes are
 * covered, and same-origin requests (the Swagger UI, for example) are not
 * treated as CORS.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsFilterConfiguration {

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600;

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        var registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
