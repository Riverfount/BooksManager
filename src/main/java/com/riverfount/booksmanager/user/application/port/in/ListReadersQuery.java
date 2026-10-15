package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing readers (RF07, HU25). The name
 * matches by "contains" and the document by equality, after normalization;
 * blank texts mean no filter. {@code active} always filters (the API
 * defaults it to true); a null {@code blocked} means no filter on it.
 */
public record ListReadersQuery(String name, String document, boolean active, Boolean blocked, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "name");
    public static final String DEFAULT_SORT = "name";

    public ListReadersQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar leitores por: " + page.sortBy()
                    + " (use " + String.join(" ou ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
        name = name == null || name.isBlank() ? null : name.strip();
        document = document == null || document.isBlank() ? null : document.strip();
    }
}
