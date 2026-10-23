package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for Author persistence, in the domain's language.
 */
public interface AuthorRepository {

    Author save(Author author);

    Optional<Author> findById(Long id);

    List<Author> findAllByIds(Set<Long> ids);

    /**
     * The active authors whose name is one of the given names, ignoring case, in one query. An author that is
     * inactive is not a candidate to be reused, so it is not returned.
     */
    List<Author> findActiveByNames(Set<String> names);

    /**
     * Lists authors by active flag and, when nameContains is not null, by
     * name containing it (case-insensitive). The sort field is one of
     * ListAuthorsQuery.SORTABLE_FIELDS.
     */
    PageResult<Author> findAll(String nameContains, boolean active, PageQuery page);
}
