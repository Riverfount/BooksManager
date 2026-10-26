package com.riverfount.booksmanager.shared.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * The endpoint (or every endpoint of the controller) is a route of the reader (Phase 6, RNF08): it may only be called
 * by the access of a reader, never by an administrator or an operator, and it lives under {@code /api/v1/me}. The
 * reader is told by the token, not by the request: the route learns who they are with {@link CurrentReader}, and what
 * the reader reaches is what is theirs ({@code Ownership}). The architecture rule keeps the marker and the path
 * together (see AccessRules), and the security chain refuses every other profile on {@code /api/v1/me/**}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@PreAuthorize("hasRole('READER')")
public @interface ReaderOnly {
}
