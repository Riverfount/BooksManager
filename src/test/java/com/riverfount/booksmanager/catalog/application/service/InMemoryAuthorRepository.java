package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    @Override
    public PageResult<Author> findAll(String nameContains, boolean active, PageQuery page) {
        Comparator<Author> comparator = switch (page.sortBy()) {
            case "id" -> Comparator.comparing(Author::getId);
            case "name" -> Comparator.comparing(Author::getName).thenComparing(Author::getId);
            default -> throw new IllegalArgumentException("campo de ordenação não suportado: " + page.sortBy());
        };
        if (page.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        var matching = authors.values().stream()
                .filter(author -> author.isActive() == active)
                .filter(author -> nameContains == null || author.getName().toLowerCase(Locale.ROOT)
                        .contains(nameContains.toLowerCase(Locale.ROOT)))
                .sorted(comparator)
                .toList();
        var items = matching.stream()
                .skip((long) page.page() * page.size())
                .limit(page.size())
                .toList();
        return new PageResult<>(items, page.page(), page.size(), matching.size());
    }
}
