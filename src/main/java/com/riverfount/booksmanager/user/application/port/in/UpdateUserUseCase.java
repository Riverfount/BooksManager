package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.User;

/** Edits the profile of a system user (RF09, HU28). */
public interface UpdateUserUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException for an unknown user
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException when the last active
     *         administrator would lose the profile
     */
    User update(UpdateUserCommand command);
}
