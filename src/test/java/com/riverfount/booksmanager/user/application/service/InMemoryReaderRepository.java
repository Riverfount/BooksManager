package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import com.riverfount.booksmanager.user.application.port.out.ReaderFilter;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import com.riverfount.booksmanager.user.domain.Reader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * In-memory implementation of ReaderRepository, used only in the
 * application services' unit tests (no Spring, no database). Like the
 * database, it refuses a duplicate document. Does not need to be
 * thread-safe: it is a test fake, used from a single thread.
 */
class InMemoryReaderRepository implements ReaderRepository {

    private static final Map<String, Comparator<Reader>> COMPARATORS = Map.of(
            "id", Comparator.comparing(Reader::getId),
            "name", Comparator.comparing(Reader::getName),
            "document", Comparator.comparing(Reader::getDocument));

    private final Map<Long, Reader> readers = new HashMap<>();
    private long nextId = 1;

    /**
     * Like the JPA adapter, the repository never hands out the stored
     * instance: changing a loaded reader has no effect until it is saved.
     */
    private static Reader copyWithId(Reader reader, Long id) {
        return Reader.reconstitute(id, reader.getName(), reader.getDocument(), reader.getEmail(),
                reader.getPhone(), reader.getBirthDate(), reader.getGuardian(), reader.isActive(),
                reader.isBlocked());
    }

    @Override
    public Reader save(Reader reader) {
        if (existsByDocumentAndIdNot(reader.getDocument(), reader.getId())) {
            throw new BusinessRuleException("já existe um leitor com o documento '" + reader.getDocument() + "'");
        }
        var id = reader.getId() != null ? reader.getId() : nextId++;
        readers.put(id, copyWithId(reader, id));
        return copyWithId(reader, id);
    }

    @Override
    public Optional<Reader> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(readers.get(id)).map(reader -> copyWithId(reader, id));
    }

    @Override
    public Map<Long, String> findNamesByIds(Set<Long> ids) {
        return readers.values().stream().filter(reader -> ids.contains(reader.getId()))
                .collect(Collectors.toMap(Reader::getId, Reader::getName));
    }

    @Override
    public Map<Long, ReaderContact> findContactsByIds(Set<Long> ids) {
        return readers.values().stream().filter(reader -> ids.contains(reader.getId()))
                .collect(Collectors.toMap(Reader::getId, reader -> new ReaderContact(reader.getName(),
                        reader.getEmail())));
    }

    @Override
    public boolean existsByDocument(String document) {
        return readers.values().stream().anyMatch(reader -> reader.getDocument().equals(document));
    }

    @Override
    public boolean existsByDocumentAndIdNot(String document, Long id) {
        return readers.values().stream()
                .anyMatch(reader -> reader.getDocument().equals(document) && !reader.getId().equals(id));
    }

    @Override
    public List<Reader> findActiveByEmail(String email) {
        var normalized = email == null ? "" : email.strip().toLowerCase(java.util.Locale.ROOT);
        return readers.values().stream()
                .filter(reader -> reader.isActive() && reader.getEmail().toLowerCase(java.util.Locale.ROOT).equals(normalized))
                .sorted(java.util.Comparator.comparing(Reader::getId))
                .map(reader -> copyWithId(reader, reader.getId()))
                .toList();
    }

    @Override
    public PageResult<Reader> findAll(ReaderFilter filter, PageQuery page) {
        var comparator = COMPARATORS.get(page.sortBy());
        if (comparator == null) {
            throw new IllegalArgumentException("campo de ordenação não suportado: " + page.sortBy());
        }
        if (page.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        List<Reader> sorted = readers.values().stream()
                .filter(reader -> filter.name() == null || reader.getName().toLowerCase(Locale.ROOT)
                        .contains(filter.name().toLowerCase(Locale.ROOT)))
                .filter(reader -> filter.document() == null || reader.getDocument().equals(filter.document()))
                .filter(reader -> filter.active() == null || reader.isActive() == filter.active())
                .filter(reader -> filter.blocked() == null || reader.isBlocked() == filter.blocked())
                .sorted(comparator.thenComparing(Reader::getId))
                .toList();
        var items = sorted.stream().skip((long) page.page() * page.size()).limit(page.size())
                .map(reader -> copyWithId(reader, reader.getId())).toList();
        return new PageResult<>(items, page.page(), page.size(), sorted.size());
    }
}
