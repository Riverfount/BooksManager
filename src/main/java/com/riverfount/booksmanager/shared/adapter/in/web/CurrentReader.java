package com.riverfount.booksmanager.shared.adapter.in.web;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@code Long} parameter of a route of the reader that receives the id of the reader who is logged in
 * (Phase 6). It is the only way a route learns it: the id comes from the {@code readerId} claim of the access token,
 * never from the path, a parameter or the body, so a reader cannot ask for someone else's data by changing the
 * request. A token without a valid reader is refused with 403. It is hidden from the OpenAPI contract, since the
 * caller sends nothing for it.
 */
@Parameter(hidden = true)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentReader {

    /** The claim of the access token that carries the reader of an access (set by the login, see the security module). */
    String CLAIM = "readerId";
}
