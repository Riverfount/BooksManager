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

    // a null bookId or status means "no filter on that field"
    @Query("""
            select c from CopyJpaEntity c
            where c.active = :active
              and (:bookId is null or c.bookId = :bookId)
              and (:status is null or c.status = :status)
            """)
    Page<CopyJpaEntity> search(@Param("bookId") Long bookId, @Param("status") CopyStatus status,
            @Param("active") boolean active, Pageable pageable);

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
}
