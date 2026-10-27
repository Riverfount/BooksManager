package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.domain.FineStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FineJpaRepository extends JpaRepository<FineJpaEntity, Long> {

    /** Listing. A null parameter means "no filter on that field". */
    @Query("""
            select f from FineJpaEntity f
            where (:readerId is null or f.readerId = :readerId)
              and (:loanId is null or f.loanId = :loanId)
              and (:status is null or f.status = :status)
            """)
    Page<FineJpaEntity> search(@Param("readerId") Long readerId, @Param("loanId") Long loanId,
            @Param("status") FineStatus status, Pageable pageable);

    List<FineJpaEntity> findByLoanIdIn(Collection<Long> loanIds);

    /** The total of the fines of the reader in the given status; zero when there is none. */
    @Query("""
            select coalesce(sum(f.amount), 0) from FineJpaEntity f
            where f.readerId = :readerId and f.status = :status
            """)
    BigDecimal sumAmountByReaderAndStatus(@Param("readerId") Long readerId, @Param("status") FineStatus status);
}
