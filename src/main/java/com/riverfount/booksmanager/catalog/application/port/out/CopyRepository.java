package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for Copy persistence, in the domain's language.
 */
public interface CopyRepository {

    Copy save(Copy copy);

    Optional<Copy> findById(Long id);

    /** The copies with the given ids, in one query; an id that does not exist is absent from the map. */
    Map<Long, Copy> findAllByIds(Set<Long> ids);

    boolean existsByAssetCode(String assetCode);

    /**
     * The highest number among the asset codes of the suggested format ({@code PREFIX-} and one to
     * {@link com.riverfount.booksmanager.catalog.domain.AssetCodes#MAX_DIGITS} digits), inactive copies included; zero
     * when there is none. A code that does not match (another prefix, letters in the number, a number too long to be a
     * sequence) is ignored. The prefix is taken literally, never as a pattern. One query, however many copies exist.
     */
    long findHighestAssetCodeNumber(String prefix);

    /**
     * Lists copies by active flag, optionally filtered by book, status and
     * asset code (null means no filter; the code must be the whole code, and
     * its case does not matter). The sort field is one of
     * ListCopiesQuery.SORTABLE_FIELDS.
     */
    PageResult<Copy> findAll(Long bookId, CopyStatus status, String assetCode, boolean active, PageQuery page);

    /**
     * Counts, for each given book, its copies that can be borrowed: active
     * and with status AVAILABLE (RF05). One aggregated query for all the
     * books; a book with no available copy is absent from the map.
     */
    Map<Long, Long> countAvailableByBookIds(Set<Long> bookIds);

    /**
     * Counts, for each given book, its active copies whatever their status
     * (RF05). One aggregated query for all the books; a book with no
     * active copy is absent from the map.
     */
    Map<Long, Long> countActiveByBookIds(Set<Long> bookIds);

    /**
     * Counts all the copies grouped by status and by active flag (RF25, RN14), in one aggregated query. A
     * combination with no copy is absent, and the order is not defined.
     */
    List<CopyStatusCount> countByStatus();
}
