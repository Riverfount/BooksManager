package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Author;
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
}
