package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.User;

/**
 * Creates a system user (RF09, HU28). The password must meet the password
 * policy and is stored only as a hash; the login must be new.
 */
public interface CreateUserUseCase {

    /** @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException for a weak password, an invalid or repeated login, or a missing profile */
    User create(CreateUserCommand command);
}
