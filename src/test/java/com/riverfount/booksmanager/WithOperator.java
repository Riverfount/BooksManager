package com.riverfount.booksmanager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithMockUser;

/** Runs the test as an authenticated operator (profile OPERATOR), the usual caller of the day to day routes. */
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(roles = "OPERATOR")
public @interface WithOperator {
}
