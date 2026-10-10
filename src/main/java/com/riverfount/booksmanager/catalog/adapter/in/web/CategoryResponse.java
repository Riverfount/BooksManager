package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Category;

record CategoryResponse(Long id, String name, boolean active) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.isActive());
    }
}
