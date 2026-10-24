package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Copy;

/**
 * Reactivates an inactivated copy (RF04, RN14), keeping its status. It is refused while its book is inactive;
 * reactivating an active copy is a no-op.
 */
public interface ReactivateCopyUseCase {

    Copy reactivate(Long id);
}
