package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * Reactivates an inactivated book (RF01, RN14). The book is only reactivated when its category and all its authors
 * are active (the refusal names the inactive ones); reactivating an active book is a no-op. Its copies are not
 * touched: inactivating the book did not inactivate them.
 */
public interface ReactivateBookUseCase {

    /** @return the book with its details, as the other book answers */
    BookWithAvailability reactivate(Long id);
}
