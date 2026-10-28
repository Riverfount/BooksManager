package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.domain.Role;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing system users (RF09, HU28). The login
 * matches by "contains", ignoring case; a blank login means no filter.
 * {@code active} always filters (the API defaults it to true). Only the id and
 * the login can be sorted by, so no secret (the hash) can be used to order. A null {@code role} lists every profile,
 * the accesses of the readers included (#325); a role lists exactly that profile.
 */
public record ListUsersQuery(String login, boolean active, Role role, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "login");
    public static final String DEFAULT_SORT = "login";

    public ListUsersQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar usuários por: " + page.sortBy()
                    + " (use " + String.join(" ou ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
        login = login == null || login.isBlank() ? null : login.strip();
    }

    public ListUsersQuery(String login, boolean active, PageQuery page) {
        this(login, active, null, page);
    }
}
