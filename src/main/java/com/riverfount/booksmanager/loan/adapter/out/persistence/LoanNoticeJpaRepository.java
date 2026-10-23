package com.riverfount.booksmanager.loan.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LoanNoticeJpaRepository extends JpaRepository<LoanNoticeJpaEntity, Long> {

    List<LoanNoticeJpaEntity> findByLoanIdIn(Collection<Long> loanIds);
}
