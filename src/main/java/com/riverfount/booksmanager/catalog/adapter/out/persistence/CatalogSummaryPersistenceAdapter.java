package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.application.port.out.CatalogSummaryRepository;
import com.riverfount.booksmanager.catalog.application.port.out.CategoryCopyCount;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class CatalogSummaryPersistenceAdapter implements CatalogSummaryRepository {

    private final CategoryJpaRepository jpaRepository;

    CatalogSummaryPersistenceAdapter(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<CategoryCopyCount> countByCategory() {
        return jpaRepository.countBooksAndCopies().stream()
                .map(row -> new CategoryCopyCount(row.getId(), row.getName(), row.getActive(), row.getBooks(),
                        row.getCopies()))
                .toList();
    }
}
