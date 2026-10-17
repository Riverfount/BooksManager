package com.riverfount.booksmanager.loan.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoanJpaRepository extends JpaRepository<LoanJpaEntity, Long> {

    /**
     * Listing (RF14). A null parameter means "no filter on that field". The situation
     * is derived from the return date: active when it is null.
     */
    @Query("""
            select l from LoanJpaEntity l
            where (:readerId is null or l.readerId = :readerId)
              and (:copyId is null or l.copyId = :copyId)
              and (:active is null
                   or (:active = true and l.returnDate is null)
                   or (:active = false and l.returnDate is not null))
            """)
    Page<LoanJpaEntity> search(@Param("readerId") Long readerId, @Param("copyId") Long copyId,
            @Param("active") Boolean active, Pageable pageable);
}
