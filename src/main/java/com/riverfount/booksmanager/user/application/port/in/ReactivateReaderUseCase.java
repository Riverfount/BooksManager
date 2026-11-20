package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Reader;

/**
 * Reactivates an inactivated reader (RF07). Reactivating an active reader is a no-op that returns the reader.
 * The blocked state is kept, and the access to the system is not given back: a new invitation reopens it.
 */
public interface ReactivateReaderUseCase {

    Reader reactivate(Long id);
}
