package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Reader;

/**
 * A reader's own registration, as they see it (RF32). {@code minor} is decided today by the domain, so the front end
 * knows whether the guardian can be edited without computing an age.
 */
public record OwnProfile(Reader reader, boolean minor) {
}
