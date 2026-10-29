package com.riverfount.booksmanager.shared.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * The endpoint (or every endpoint of the controller) may be called by any logged-in profile: administrator, operator
 * or reader (RNF08). Only for routes that describe the caller and nothing else, such as {@code /users/me}. A token
 * with no profile, or none at all, gets 403 or 401 in ProblemDetail.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'READER')")
public @interface AnyProfile {
}
