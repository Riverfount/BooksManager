package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Category;

/** Reactivates an inactivated category (RF03, RN14); reactivating an active category is a no-op. */
public interface ReactivateCategoryUseCase {

    Category reactivate(Long id);
}
