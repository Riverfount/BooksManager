package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Category;

public interface RegisterCategoryUseCase {

    Category register(RegisterCategoryCommand command);
}
