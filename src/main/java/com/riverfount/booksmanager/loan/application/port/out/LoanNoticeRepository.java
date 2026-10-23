package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.LoanNotice;
import java.time.LocalDate;
import java.util.Set;

/** Outbound port that records which notices about loans were already sent (RF28), in the domain's language. */
public interface LoanNoticeRepository {

    /** The notices already sent about the given loans, in one query. */
    Set<SentLoanNotice> findSentByLoanIds(Set<Long> loanIds);

    /**
     * Records that the notice about the loan was sent on {@code sentOn}. The database allows each notice once, so a
     * second record of the same one (two runs at the same time) fails instead of being kept.
     */
    void save(Long loanId, LoanNotice notice, LocalDate sentOn);
}
