package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.out.LoanNoticeRepository;
import com.riverfount.booksmanager.loan.application.port.out.SentLoanNotice;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * In-memory implementation of LoanNoticeRepository, used only in the application services' unit tests. Like the unique
 * key of the database, it refuses the same notice twice. Does not need to be thread-safe.
 */
class InMemoryLoanNoticeRepository implements LoanNoticeRepository {

    private final Set<SentLoanNotice> sent = new HashSet<>();

    @Override
    public Set<SentLoanNotice> findSentByLoanIds(Set<Long> loanIds) {
        return sent.stream().filter(notice -> loanIds.contains(notice.loanId())).collect(Collectors.toSet());
    }

    @Override
    public void save(Long loanId, LoanNotice notice, LocalDate sentOn) {
        if (!sent.add(new SentLoanNotice(loanId, notice))) {
            throw new IllegalStateException("the notice was already recorded: " + notice);
        }
    }

    Set<SentLoanNotice> all() {
        return Set.copyOf(sent);
    }
}
