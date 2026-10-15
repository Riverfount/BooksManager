package com.riverfount.booksmanager.security.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on the authorization by profile declared on the controllers
 * ({@code @AdminOnly}, {@code @AdminOrOperator}), checked before the method
 * runs (RNF08). Kept apart from the security chain so that the controller
 * tests, which do not load the chain, can import just this.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfiguration {
}
