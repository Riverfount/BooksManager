package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.PageResult;

public interface ListCategoriesUseCase {

    PageResult<Category> list(ListCategoriesQuery query);
}
