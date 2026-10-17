package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Sorting and paging shared by the in-memory repositories of the loan module's tests. */
final class InMemoryPages {

    private InMemoryPages() {
    }

    static <T> PageResult<T> of(Collection<T> all, Predicate<T> filter, Map<String, Comparator<T>> comparators,
            Function<T, Long> id, PageQuery page) {
        var comparator = comparators.get(page.sortBy());
        if (comparator == null) {
            throw new IllegalArgumentException("campo de ordenação não suportado: " + page.sortBy());
        }
        if (page.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        var sorted = all.stream().filter(filter)
                .sorted(comparator.thenComparing(Comparator.comparing(id))).toList();
        var items = sorted.stream().skip((long) page.page() * page.size()).limit(page.size()).toList();
        return new PageResult<>(items, page.page(), page.size(), sorted.size());
    }
}
