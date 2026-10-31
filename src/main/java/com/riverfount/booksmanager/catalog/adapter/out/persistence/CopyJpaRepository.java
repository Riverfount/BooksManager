package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CopyJpaRepository extends JpaRepository<CopyJpaEntity, Long> {

    boolean existsByAssetCode(String assetCode);

    /**
     * The highest number among the codes {@code PREFIX-<1 to 9 digits>}, zero when there is none. The prefix is
     * compared with plain string functions, not a regular expression, so it is taken literally; only the digits part
     * is matched against a pattern, and nine digits at most can never overflow a bigint.
     */
    @Query(value = """
            select coalesce(max(cast(substr(asset_code, length(cast(:prefix as text)) + 2) as bigint)), 0)
            from copy
            where left(asset_code, length(cast(:prefix as text)) + 1) = cast(:prefix as text) || '-'
              and substr(asset_code, length(cast(:prefix as text)) + 2) ~ '^[0-9]{1,9}$'
            """, nativeQuery = true)
    Long findHighestAssetCodeNumber(@Param("prefix") String prefix);

    // a null bookId, status or assetCode means "no filter on that field"; the assetCode comes lowercase
    @Query("""
            select c from CopyJpaEntity c
            where c.active = :active
              and (:bookId is null or c.bookId = :bookId)
              and (:status is null or c.status = :status)
              and (:assetCode is null or lower(c.assetCode) = :assetCode)
            """)
    Page<CopyJpaEntity> search(@Param("bookId") Long bookId, @Param("status") CopyStatus status,
            @Param("assetCode") String assetCode, @Param("active") boolean active, Pageable pageable);

    /** Active AVAILABLE copies per book, in one grouped query (RF05). */
    @Query("""
            select c.bookId as bookId, count(c) as total from CopyJpaEntity c
            where c.bookId in :bookIds and c.active = true
              and c.status = com.riverfount.booksmanager.catalog.domain.CopyStatus.AVAILABLE
            group by c.bookId
            """)
    List<CopiesCount> countAvailableByBookIds(@Param("bookIds") Collection<Long> bookIds);

    /** Active copies per book, whatever their status, in one grouped query (RF05). */
    @Query("""
            select c.bookId as bookId, count(c) as total from CopyJpaEntity c
            where c.bookId in :bookIds and c.active = true
            group by c.bookId
            """)
    List<CopiesCount> countActiveByBookIds(@Param("bookIds") Collection<Long> bookIds);

    interface CopiesCount {

        Long getBookId();

        Long getTotal();
    }

    /** One row of {@link #countByStatus()}: the copies with a status, active or not. */
    interface StatusCount {
        com.riverfount.booksmanager.catalog.domain.CopyStatus getStatus();

        Boolean getActive();

        Long getTotal();
    }

    /** All the copies per status and active flag, in one grouped query (RF25). */
    @Query("""
            select c.status as status, c.active as active, count(c) as total from CopyJpaEntity c
            group by c.status, c.active
            """)
    List<StatusCount> countByStatus();
}
