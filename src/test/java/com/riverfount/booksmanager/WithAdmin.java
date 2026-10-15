package com.riverfount.booksmanager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithMockUser;

/** Runs the test as an authenticated administrator (profile ADMIN). */
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(roles = "ADMIN")
public @interface WithAdmin {
}
