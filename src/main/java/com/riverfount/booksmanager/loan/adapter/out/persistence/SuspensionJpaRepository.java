package com.riverfount.booksmanager.loan.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SuspensionJpaRepository extends JpaRepository<SuspensionJpaEntity, Long> {

    /**
     * Listing. A null parameter means "no filter on that field". "In force on a day"
     * has the meaning of {@code Suspension.isInForce}: from the start date up to the day
     * before the end, which is the early end when it came before the calculated one. "Not in force"
     * is the complement.
     */
    @Query("""
            select s from SuspensionJpaEntity s
            where (:readerId is null or s.readerId = :readerId)
              and (:loanId is null or s.loanId = :loanId)
              and (cast(:day as LocalDate) is null
                   or (s.startDate <= :day and :day < least(s.endDate, coalesce(s.endedEarlyOn, s.endDate))))
              and (cast(:notDay as LocalDate) is null
                   or not (s.startDate <= :notDay
                           and :notDay < least(s.endDate, coalesce(s.endedEarlyOn, s.endDate))))
            """)
    Page<SuspensionJpaEntity> search(@Param("readerId") Long readerId, @Param("loanId") Long loanId,
            @Param("day") LocalDate day, @Param("notDay") LocalDate notDay, Pageable pageable);

    /** The readers (distinct) with a suspension in force on the day; the same condition as {@link #search}. */
    @Query("""
            select count(distinct s.readerId) from SuspensionJpaEntity s
            where s.startDate <= :day and :day < least(s.endDate, coalesce(s.endedEarlyOn, s.endDate))
            """)
    long countReadersInForceOn(@Param("day") LocalDate day);

    List<SuspensionJpaEntity> findByLoanIdInOrderByIdAsc(Collection<Long> loanIds);
}
