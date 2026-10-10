package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.Copy;
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
}
