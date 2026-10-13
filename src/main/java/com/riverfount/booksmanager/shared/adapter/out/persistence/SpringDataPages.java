package com.riverfount.booksmanager.shared.adapter.out.persistence;

import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Converts between the framework-free pagination types (PageQuery,
 * PageResult) and Spring Data's, so that Pageable stays inside the
 * persistence adapters. Shared by every module's adapters.
 */
public final class SpringDataPages {

    private static final String ID_PROPERTY = "id";

    private SpringDataPages() {
    }

    /**
     * The sort field must be an entity property name (each listing's query
     * validates it). Ties are broken by id so pages are stable.
     */
    public static Pageable toPageable(PageQuery page) {
        var direction = page.direction() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        var sort = Sort.by(direction, page.sortBy());
        if (!ID_PROPERTY.equals(page.sortBy())) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, ID_PROPERTY));
        }
        return PageRequest.of(page.page(), page.size(), sort);
    }

    public static <E, D> PageResult<D> toPageResult(Page<E> result, Function<? super E, D> toDomain) {
        return new PageResult<>(result.map(toDomain).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements());
    }
}
