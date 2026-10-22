package com.riverfount.booksmanager.loan.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoanJpaRepository extends JpaRepository<LoanJpaEntity, Long> {

    /**
     * Listing (RF14). A null parameter means "no filter on that field". The situation
     * is derived from the return date: active when it is null. Overdue on a day has the meaning of
     * {@code Loan.isOverdue}: still open, with the due date before the day.
     */
    @Query("""
            select l from LoanJpaEntity l
            where (:readerId is null or l.readerId = :readerId)
              and (:copyId is null or l.copyId = :copyId)
              and (:active is null
                   or (:active = true and l.returnDate is null)
                   or (:active = false and l.returnDate is not null))
              and (cast(:overdueDay as LocalDate) is null
                   or (l.returnDate is null and l.dueDate < :overdueDay))
            """)
    Page<LoanJpaEntity> search(@Param("readerId") Long readerId, @Param("copyId") Long copyId,
            @Param("active") Boolean active, @Param("overdueDay") LocalDate overdueDay,
            Pageable pageable);

    /** The count behind {@link #search}: same conditions, no rows loaded. Keep both in step. */
    @Query("""
            select count(l) from LoanJpaEntity l
            where (:readerId is null or l.readerId = :readerId)
              and (:copyId is null or l.copyId = :copyId)
              and (:active is null
                   or (:active = true and l.returnDate is null)
                   or (:active = false and l.returnDate is not null))
              and (cast(:overdueDay as LocalDate) is null
                   or (l.returnDate is null and l.dueDate < :overdueDay))
            """)
    long countMatching(@Param("readerId") Long readerId, @Param("copyId") Long copyId,
            @Param("active") Boolean active, @Param("overdueDay") LocalDate overdueDay);

    /** The loans picked up in the period (both ends inclusive) per copy: rows are {@code [copyId, total]}. */
    @Query("""
            select l.copyId, count(l) from LoanJpaEntity l
            where l.pickupDate >= :from and l.pickupDate <= :to
            group by l.copyId
            """)
    List<Object[]> countByCopyBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select l.copyId from LoanJpaEntity l where l.readerId = :readerId and l.returnDate is null")
    Set<Long> findActiveCopyIdsByReaderId(@Param("readerId") Long readerId);
}
