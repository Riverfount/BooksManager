package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for Copy persistence, in the domain's language.
 */
public interface CopyRepository {

    Copy save(Copy copy);

    Optional<Copy> findById(Long id);

    boolean existsByAssetCode(String assetCode);

    /**
     * Lists copies by active flag, optionally filtered by book and status
     * (null means no filter). The sort field is one of
     * ListCopiesQuery.SORTABLE_FIELDS.
     */
    PageResult<Copy> findAll(Long bookId, CopyStatus status, boolean active, PageQuery page);

    /**
     * Counts, for each given book, its copies that can be borrowed: active
     * and with status AVAILABLE (RF05). One aggregated query for all the
     * books; a book with no available copy is absent from the map.
     */
    Map<Long, Long> countAvailableByBookIds(Set<Long> bookIds);
}
