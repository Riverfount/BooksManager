package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Category;

public interface UpdateCategoryUseCase {

    Category update(UpdateCategoryCommand command);
}
