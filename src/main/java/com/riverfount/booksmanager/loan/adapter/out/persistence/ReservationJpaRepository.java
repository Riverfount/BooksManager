package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {

    /** Listing. A null parameter means "no filter on that field". */
    @Query("""
            select r from ReservationJpaEntity r
            where (:readerId is null or r.readerId = :readerId)
              and (:bookId is null or r.bookId = :bookId)
              and (:status is null or r.status = :status)
            """)
    Page<ReservationJpaEntity> search(@Param("readerId") Long readerId, @Param("bookId") Long bookId,
            @Param("status") ReservationStatus status, Pageable pageable);

    /** The queue of a book is its active reservations with no copy, in order of arrival (the identity order). */
    Optional<ReservationJpaEntity> findFirstByBookIdAndStatusAndCopyIdIsNullOrderByIdAsc(Long bookId,
            ReservationStatus status);

    /** How many of the waiting reservations of the book arrived up to the given one, itself included. */
    long countByBookIdAndStatusAndCopyIdIsNullAndIdLessThanEqual(Long bookId, ReservationStatus status, Long id);

    long countByReaderIdAndStatus(Long readerId, ReservationStatus status);

    List<ReservationJpaEntity> findByStatusAndCopyIdIsNotNullAndPickupDeadlineBeforeOrderByPickupDeadlineAscIdAsc(
            ReservationStatus status, LocalDate today);
}
