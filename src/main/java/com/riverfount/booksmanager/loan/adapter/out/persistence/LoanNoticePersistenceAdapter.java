package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.application.port.out.LoanNoticeRepository;
import com.riverfount.booksmanager.loan.application.port.out.SentLoanNotice;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class LoanNoticePersistenceAdapter implements LoanNoticeRepository {

    private final LoanNoticeJpaRepository jpaRepository;

    LoanNoticePersistenceAdapter(LoanNoticeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Set<SentLoanNotice> findSentByLoanIds(Set<Long> loanIds) {
        if (loanIds.isEmpty()) {
            return Set.of();
        }
        return jpaRepository.findByLoanIdIn(loanIds).stream()
                .map(entity -> new SentLoanNotice(entity.getLoanId(),
                        new LoanNotice(entity.getKind(), entity.getDueDate(), entity.getBand())))
                .collect(Collectors.toSet());
    }

    @Override
    public void save(Long loanId, LoanNotice notice, LocalDate sentOn) {
        // flushed here so that a duplicate (the unique key) fails now, before the message is sent
        jpaRepository.saveAndFlush(
                new LoanNoticeJpaEntity(loanId, notice.kind(), notice.dueDate(), notice.band(), sentOn));
    }
}
