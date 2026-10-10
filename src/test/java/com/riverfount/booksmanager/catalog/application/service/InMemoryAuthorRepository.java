package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory implementation of AuthorRepository, used only in the
 * application services' unit tests (no Spring, no database). Does not
 * need to be thread-safe: it is a test fake, used from a single thread.
 */
class InMemoryAuthorRepository implements AuthorRepository {

    private final Map<Long, Author> authors = new HashMap<>();
    private long nextId = 1;

    @Override
    public Author save(Author author) {
        var id = author.getId() != null ? author.getId() : nextId++;
        var saved = Author.reconstitute(id, author.getName(), author.isActive());
        authors.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Author> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(authors.get(id));
    }

    @Override
    public List<Author> findAllByIds(Set<Long> ids) {
        return ids.stream().map(authors::get).filter(Objects::nonNull).toList();
    }
}
