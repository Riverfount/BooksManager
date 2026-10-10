package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Category;
import java.util.Optional;

public interface GetCategoryUseCase {

    Optional<Category> findById(Long id);
}
