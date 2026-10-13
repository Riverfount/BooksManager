package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.GetCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.CategoryRepository;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registering, querying and listing Category: fetches what the
 * domain needs to decide, calls the domain and persists. The business
 * decision itself (unique name, validations, RN14) lives entirely in
 * Category.
 */
@Service
@Transactional
class CategoryService implements RegisterCategoryUseCase, GetCategoryUseCase, ListCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Category register(RegisterCategoryCommand command) {
        Category.validateUniqueName(command.name(), categoryRepository.existsByName(command.name()));
        return categoryRepository.save(Category.create(command.name()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Category> list(ListCategoriesQuery query) {
        return categoryRepository.findAll(query.name(), query.active(), query.page());
    }
}
