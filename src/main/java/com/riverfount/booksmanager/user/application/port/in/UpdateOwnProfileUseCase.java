package com.riverfount.booksmanager.user.application.port.in;

/** The reader maintains their own contact data (Phase 6, RF32, RN22). */
public interface UpdateOwnProfileUseCase {

    /**
     * Changes the phone, the e-mail and the guardian of the reader; the identity data and the state are untouched.
     * Changing the e-mail asks for the current password.
     *
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reader does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if a value is invalid, the guardian
     *         cannot be changed (the reader is an adult), the reader is inactive or the current password is wrong
     */
    OwnProfile update(UpdateOwnProfileCommand command);
}
