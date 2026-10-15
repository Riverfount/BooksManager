package com.riverfount.booksmanager.shared.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * The endpoint (or every endpoint of the controller) may be called by an
 * administrator or an operator (RNF08): the catalog, the readers and the day to
 * day operations. A token with any other profile, or none, gets 403 in ProblemDetail.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public @interface AdminOrOperator {
}
