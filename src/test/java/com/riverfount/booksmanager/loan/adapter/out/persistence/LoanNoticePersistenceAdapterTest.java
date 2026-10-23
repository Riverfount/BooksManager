package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.loan.application.port.out.SentLoanNotice;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import com.riverfount.booksmanager.loan.domain.LoanNoticeKind;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The record of the loan notices already sent (RF28) against a real PostgreSQL: it is read back as the domain value,
 * in one query for many loans, and the unique key refuses the same notice twice.
 */
@PersistenceTest
class LoanNoticePersistenceAdapterTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 11, 1);
    private static final LocalDate DUE = PICKUP.plusDays(14);

    @Autowired
    private LoanNoticePersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loanId;
    private long otherLoanId;

    @BeforeEach
    void createLoans() {
        var data = new LoanTestData(jdbcTemplate);
        var readerId = data.newReader();
        loanId = data.newActiveLoan(data.newCopy(), readerId, PICKUP, DUE);
        otherLoanId = data.newActiveLoan(data.newCopy(), readerId, PICKUP, DUE);
    }

    @Test
    void shouldReadBackWhatWasRecordedForTheGivenLoansOnly() {
        var dueSoon = new LoanNotice(LoanNoticeKind.DUE_SOON, DUE, 0);
        var overdue = new LoanNotice(LoanNoticeKind.OVERDUE, DUE, 1);
        adapter.save(loanId, dueSoon, DUE.minusDays(1));
        adapter.save(loanId, overdue, DUE.plusDays(8));
        adapter.save(otherLoanId, dueSoon, DUE.minusDays(1));

        assertThat(adapter.findSentByLoanIds(Set.of(loanId)))
                .containsExactlyInAnyOrder(new SentLoanNotice(loanId, dueSoon), new SentLoanNotice(loanId, overdue));
        assertThat(adapter.findSentByLoanIds(Set.of(loanId, otherLoanId))).hasSize(3);
    }

    @Test
    void shouldFindNothingForNoLoansOrForALoanWithoutNotices() {
        assertThat(adapter.findSentByLoanIds(Set.of())).isEmpty();
        assertThat(adapter.findSentByLoanIds(Set.of(loanId))).isEmpty();
    }

    @Test
    void shouldRefuseTheSameNoticeTwiceSoTwoRunsCannotBothRecordIt() {
        var notice = new LoanNotice(LoanNoticeKind.DUE_SOON, DUE, 0);
        adapter.save(loanId, notice, DUE.minusDays(1));

        assertThatThrownBy(() -> adapter.save(loanId, notice, DUE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldKeepTheNoticeOfANewDueDateApartFromTheOldOne() {
        adapter.save(loanId, new LoanNotice(LoanNoticeKind.DUE_SOON, DUE, 0), DUE.minusDays(1));
        adapter.save(loanId, new LoanNotice(LoanNoticeKind.DUE_SOON, DUE.plusDays(14), 0), DUE.plusDays(13));

        assertThat(adapter.findSentByLoanIds(Set.of(loanId))).hasSize(2);
    }
}
