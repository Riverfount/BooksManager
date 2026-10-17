package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CatalogSummary;
import com.riverfount.booksmanager.catalog.application.port.in.CategoryCopies;
import com.riverfount.booksmanager.catalog.application.port.in.GetCatalogSummaryUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.CatalogSummaryRepository;
import com.riverfount.booksmanager.catalog.application.port.out.CategoryCopyCount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the catalog offers the loan module for the dashboard (RF25). It is a class of its own, apart from
 * the other catalog services, because it serves another module and the catalog never depends on the loan.
 * It only reads: the total is the sum of the copies of every category, so the two always agree.
 */
@Service
@Transactional(readOnly = true)
class CatalogSummaryService implements GetCatalogSummaryUseCase {

    private final CatalogSummaryRepository repository;

    CatalogSummaryService(CatalogSummaryRepository repository) {
        this.repository = repository;
    }

    @Override
    public CatalogSummary get() {
        var rows = repository.countByCategory();
        var total = rows.stream().mapToLong(CategoryCopyCount::copies).sum();
        var categories = rows.stream()
                .filter(row -> row.categoryActive() || row.books() > 0 || row.copies() > 0)
                .map(row -> new CategoryCopies(row.categoryId(), row.categoryName(), row.books(), row.copies()))
                .toList();
        return new CatalogSummary(total, categories);
    }
}
