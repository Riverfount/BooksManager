package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sorts and paginates in memory for the repository fakes, mirroring what
 * the JPA adapters do: unknown sort fields fail, and ties are broken by
 * id so pages are stable.
 */
final class InMemoryPagination {

    private InMemoryPagination() {
    }

    static <T> PageResult<T> page(Stream<T> matching, PageQuery page,
            Map<String, Comparator<T>> comparatorsBySortField, Comparator<T> byId) {
        var comparator = comparatorsBySortField.get(page.sortBy());
        if (comparator == null) {
            throw new IllegalArgumentException("campo de ordenação não suportado: " + page.sortBy());
        }
        if (page.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        List<T> sorted = matching.sorted(comparator.thenComparing(byId)).toList();
        var items = sorted.stream()
                .skip((long) page.page() * page.size())
                .limit(page.size())
                .toList();
        return new PageResult<>(items, page.page(), page.size(), sorted.size());
    }
}
