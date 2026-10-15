package com.riverfount.booksmanager.shared.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * The endpoint (or every endpoint of the controller) may only be called by an
 * administrator (RNF08, RF09): the system users and the library configuration.
 * A caller with another profile gets 403 in ProblemDetail.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {
}
