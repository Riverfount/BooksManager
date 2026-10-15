package com.riverfount.booksmanager.shared.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The endpoint is public on purpose (login, token renewal and logout), so it
 * needs no profile. This only records the decision, so that the architecture
 * rule can tell "public by design" from "forgotten": what actually lets
 * anonymous callers in is the list of public routes of the security chain
 * (SecurityConfiguration), which must include the route.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PublicEndpoint {
}
