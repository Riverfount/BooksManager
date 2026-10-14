package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;
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
}
