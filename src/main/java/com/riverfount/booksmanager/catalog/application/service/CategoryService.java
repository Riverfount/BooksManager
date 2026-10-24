package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.DeactivateCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.ReactivateCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.CategoryRepository;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registering, querying, listing, updating and deactivating
 * Category: fetches what the domain needs to decide, calls the domain and
 * persists. The business decision itself (unique name, validations, RN14)
 * lives entirely in Category.
 */
@Service
@Transactional
class CategoryService implements RegisterCategoryUseCase, GetCategoryUseCase, ListCategoriesUseCase,
        UpdateCategoryUseCase, DeactivateCategoryUseCase, ReactivateCategoryUseCase {

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

    @Override
    public Category update(UpdateCategoryCommand command) {
        var category = loadExisting(command.id());
        if (command.name() != null) {
            // the category itself is excluded, so keeping the current name
            // is not a duplicate
            Category.validateUniqueName(command.name(),
                    categoryRepository.existsByNameAndIdNot(command.name(), category.getId()));
            category.rename(command.name());
        }
        return categoryRepository.save(category);
    }

    @Override
    public void deactivate(Long id) {
        var category = loadExisting(id);
        category.deactivate();
        categoryRepository.save(category);
    }

    @Override
    public Category reactivate(Long id) {
        var category = loadExisting(id);
        category.reactivate();
        return categoryRepository.save(category);
    }

    private Category loadExisting(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("categoria não encontrada: " + id));
    }
}
