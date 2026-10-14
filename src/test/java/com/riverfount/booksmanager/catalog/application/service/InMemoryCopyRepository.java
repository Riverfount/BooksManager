package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of CopyRepository, used only in the
 * application services' unit tests (no Spring, no database). Does not
 * need to be thread-safe: it is a test fake, used from a single thread.
 */
class InMemoryCopyRepository implements CopyRepository {

    private final Map<Long, Copy> copies = new HashMap<>();
    private long nextId = 1;

    @Override
    public Copy save(Copy copy) {
        var id = copy.getId() != null ? copy.getId() : nextId++;
        var saved = Copy.reconstitute(id, copy.getBookId(), copy.getAssetCode(),
                copy.getStatus(), copy.isActive(), copy.getVersion());
        copies.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Copy> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(copies.get(id));
    }

    @Override
    public boolean existsByAssetCode(String assetCode) {
        return copies.values().stream()
                .anyMatch(copy -> copy.getAssetCode().equals(assetCode));
    }

    @Override
    public PageResult<Copy> findAll(Long bookId, CopyStatus status, boolean active, PageQuery page) {
        var matching = copies.values().stream()
                .filter(copy -> copy.isActive() == active)
                .filter(copy -> bookId == null || copy.getBookId().equals(bookId))
                .filter(copy -> status == null || copy.getStatus() == status);
        return InMemoryPagination.page(matching, page, Map.of(
                "id", Comparator.comparing(Copy::getId),
                "assetCode", Comparator.comparing(Copy::getAssetCode),
                "status", Comparator.comparing(Copy::getStatus)), Comparator.comparing(Copy::getId));
    }
}
