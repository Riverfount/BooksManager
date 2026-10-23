package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The notice a loan owes the reader on a day (RF28): a warning before the due date and, once it is late, one per band
 * of seven late days. Being late is derived from the date (RN08), and the due date itself is still on time.
 */
class LoanNoticeTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 10, 19);
    private static final LocalDate DUE = PICKUP.plusDays(14); // 2026-11-02
    private static final int NOTICE_DAYS = 2;

    private static Loan loan() {
        return Loan.create(10L, 20L, PICKUP, 14);
    }

    @ParameterizedTest
    @CsvSource({"2026-10-01", "2026-10-30"})
    void shouldOweNothingBeforeTheDaysOfNotice(LocalDate today) {
        assertThat(loan().noticeDue(today, NOTICE_DAYS)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"2026-11-01", "2026-11-02"})
    void shouldOweTheDueSoonNoticeFromTheDaysOfNoticeUpToTheDueDate(LocalDate today) {
        var notice = loan().noticeDue(today, NOTICE_DAYS);

        assertThat(notice).contains(new LoanNotice(LoanNoticeKind.DUE_SOON, DUE, 0));
    }

    @Test
    void shouldOweTheDueSoonNoticeTheFirstDayOfTheWindow() {
        assertThat(loan().noticeDue(DUE.minusDays(NOTICE_DAYS), NOTICE_DAYS))
                .contains(new LoanNotice(LoanNoticeKind.DUE_SOON, DUE, 0));
        assertThat(loan().noticeDue(DUE.minusDays(NOTICE_DAYS + 1), NOTICE_DAYS)).isEmpty();
    }

    @Test
    void shouldStillBeOnTimeOnTheDueDate() {
        assertThat(loan().noticeDue(DUE, NOTICE_DAYS).orElseThrow().kind()).isEqualTo(LoanNoticeKind.DUE_SOON);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-03, 0",
            "2026-11-09, 0",
            "2026-11-10, 1",
            "2026-11-16, 1",
            "2026-11-17, 2"})
    void shouldOweTheOverdueNoticeOfTheBandOfTheLateDays(LocalDate today, int band) {
        var notice = loan().noticeDue(today, NOTICE_DAYS);

        assertThat(notice).contains(new LoanNotice(LoanNoticeKind.OVERDUE, DUE, band));
    }

    @Test
    void shouldOweNothingToAReturnedLoan() {
        var loan = loan();
        loan.returnCopy(DUE.plusDays(3));

        assertThat(loan.noticeDue(DUE.plusDays(4), NOTICE_DAYS)).isEmpty();
        assertThat(loan.noticeDue(DUE.minusDays(1), NOTICE_DAYS)).isEmpty();
    }

    @Test
    void aRenewalMovesTheDueDateSoTheNoticeIsANewOne() {
        var loan = loan();
        var before = loan.noticeDue(DUE, NOTICE_DAYS).orElseThrow();

        loan.renew(DUE, 2, 14, false);

        assertThat(loan.noticeDue(DUE, NOTICE_DAYS)).isEmpty();
        var after = loan.noticeDue(DUE.plusDays(14), NOTICE_DAYS).orElseThrow();
        assertThat(after.dueDate()).isEqualTo(DUE.plusDays(14));
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void shouldRefuseANoticeWithoutDateOrWithADaysOfNoticeBelowOne() {
        assertThatThrownBy(() -> loan().noticeDue(null, NOTICE_DAYS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan().noticeDue(DUE, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRefuseANegativeBand() {
        assertThatThrownBy(() -> new LoanNotice(LoanNoticeKind.OVERDUE, DUE, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoanNotice(null, DUE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoanNotice(LoanNoticeKind.DUE_SOON, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
