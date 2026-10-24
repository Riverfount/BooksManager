package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The loan and its rules (RF11, RF12, RF13, RN06, RN07, RN09); the date always comes by parameter. */
class LoanTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 10, 19);
    private static final int PERIOD = 14;
    private static final int RENEWAL_LIMIT = 2;
    private static final LocalDate DUE = PICKUP.plusDays(PERIOD); // 2026-11-02

    private static Loan loan() {
        return Loan.create(10L, 20L, PICKUP, PERIOD);
    }

    // creation (RF11, RN06)

    @Test
    void shouldCreateAnActiveLoanDueAfterThePeriod() {
        var loan = loan();

        assertThat(loan.getId()).isNull();
        assertThat(loan.getCopyId()).isEqualTo(10L);
        assertThat(loan.getReaderId()).isEqualTo(20L);
        assertThat(loan.getPickupDate()).isEqualTo(PICKUP);
        assertThat(loan.getDueDate()).isEqualTo(DUE);
        assertThat(loan.getReturnDate()).isNull();
        assertThat(loan.getRenewals()).isZero();
        assertThat(loan.isActive()).isTrue();
        assertThat(loan.isReturned()).isFalse();
    }

    @Test
    void theDueDateFollowsThePeriodGivenNotAConstant() {
        assertThat(Loan.create(1L, 2L, PICKUP, 7).getDueDate()).isEqualTo(PICKUP.plusDays(7));
        assertThat(Loan.create(1L, 2L, PICKUP, 30).getDueDate()).isEqualTo(PICKUP.plusDays(30));
        assertThat(Loan.create(1L, 2L, PICKUP, 1).getDueDate()).isEqualTo(PICKUP.plusDays(1));
    }

    @Test
    void theDueDateCrossesMonthYearAndLeapDayCorrectly() {
        assertThat(Loan.create(1L, 2L, LocalDate.of(2026, 12, 25), 14).getDueDate())
                .isEqualTo(LocalDate.of(2027, 1, 8));
        assertThat(Loan.create(1L, 2L, LocalDate.of(2028, 2, 20), 14).getDueDate())
                .isEqualTo(LocalDate.of(2028, 3, 5));
    }

    @Test
    void shouldRequireTheCopyTheReaderAndThePickupDate() {
        assertThatThrownBy(() -> Loan.create(null, 20L, PICKUP, PERIOD))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exemplar");
        assertThatThrownBy(() -> Loan.create(10L, null, PICKUP, PERIOD))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("leitor");
        assertThatThrownBy(() -> Loan.create(10L, 20L, null, PERIOD))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("retirada");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRefuseAPeriodBelowOneDay(int period) {
        assertThatThrownBy(() -> Loan.create(10L, 20L, PICKUP, period)).isInstanceOf(IllegalArgumentException.class);
    }

    // late or not

    @Test
    void anActiveLoanIsNotLateUntilAfterTheDueDate() {
        var loan = loan();

        assertThat(loan.isOverdue(PICKUP)).isFalse();
        assertThat(loan.isOverdue(DUE.minusDays(1))).isFalse();
        assertThat(loan.isOverdue(DUE)).as("the due date itself is still on time").isFalse();
        assertThat(loan.daysLate(DUE)).isZero();
        assertThat(loan.daysLate(DUE.minusDays(5))).isZero();
        assertThat(loan.isOverdue(DUE.plusDays(1))).isTrue();
        assertThat(loan.daysLate(DUE.plusDays(1))).isEqualTo(1);
        assertThat(loan.daysLate(DUE.plusDays(30))).isEqualTo(30);
    }

    @Test
    void daysLateAreCalendarDaysAcrossMonthsAndYears() {
        var loan = Loan.create(1L, 2L, LocalDate.of(2026, 12, 20), 14); // due 2027-01-03

        assertThat(loan.daysLate(LocalDate.of(2027, 1, 10))).isEqualTo(7);
        assertThat(loan.daysLate(LocalDate.of(2027, 3, 3))).isEqualTo(59);
    }

    // return (RF12, RN09)

    @Test
    void shouldReturnOnTheDueDateWithNoDaysLate() {
        var loan = loan();

        var daysLate = loan.returnCopy(DUE);

        assertThat(daysLate).isZero();
        assertThat(loan.getReturnDate()).isEqualTo(DUE);
        assertThat(loan.isReturned()).isTrue();
        assertThat(loan.isActive()).isFalse();
    }

    @Test
    void shouldReturnEarlyWithNoDaysLate() {
        var loan = loan();

        assertThat(loan.returnCopy(PICKUP)).isZero();
        assertThat(loan.getReturnDate()).isEqualTo(PICKUP);
    }

    @Test
    void shouldReturnLateAndTellHowManyDays() {
        var loan = loan();

        var daysLate = loan.returnCopy(DUE.plusDays(3));

        assertThat(daysLate).isEqualTo(3);
        assertThat(loan.getReturnDate()).isEqualTo(DUE.plusDays(3));
    }

    @Test
    void aReturnedLoanKeepsItsDaysLateWhateverTheDateOfTheQuestion() {
        var late = loan();
        late.returnCopy(DUE.plusDays(3));
        var early = loan();
        early.returnCopy(DUE.minusDays(2));

        assertThat(late.daysLate(DUE.plusDays(100))).isEqualTo(3);
        assertThat(early.daysLate(DUE.plusDays(100))).isZero();
        assertThat(late.isOverdue(DUE.plusDays(100))).as("a returned loan is never overdue").isFalse();
    }

    @Test
    void shouldRefuseToReturnTwiceKeepingTheFirstReturn() {
        var loan = loan();
        loan.returnCopy(DUE.plusDays(3));

        assertThatThrownBy(() -> loan.returnCopy(DUE.plusDays(10)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("encerrado");
        assertThat(loan.getReturnDate()).isEqualTo(DUE.plusDays(3));
    }

    @Test
    void shouldRefuseAReturnBeforeThePickup() {
        var loan = loan();

        assertThatThrownBy(() -> loan.returnCopy(PICKUP.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("retirada");
        assertThat(loan.isActive()).isTrue();
    }

    // renewal (RF13, RN07, RN09)

    @Test
    void shouldRenewExtendingTheDueDateByThePeriodFromTheCurrentDueDate() {
        var loan = loan();

        loan.renew(PICKUP.plusDays(3), RENEWAL_LIMIT, PERIOD, false, true);

        assertThat(loan.getRenewals()).isEqualTo(1);
        assertThat(loan.getDueDate()).as("never shorter than the time the reader already had")
                .isEqualTo(DUE.plusDays(PERIOD));
        assertThat(loan.isActive()).isTrue();
        assertThat(loan.getPickupDate()).isEqualTo(PICKUP);
    }

    @Test
    void shouldRenewOnTheDueDateItself() {
        var loan = loan();

        loan.renew(DUE, RENEWAL_LIMIT, PERIOD, false, true);

        assertThat(loan.getRenewals()).isEqualTo(1);
        assertThat(loan.getDueDate()).isEqualTo(DUE.plusDays(PERIOD));
    }

    @Test
    void theExtensionFollowsThePeriodGiven() {
        var loan = loan();

        loan.renew(PICKUP, RENEWAL_LIMIT, 7, false, true);

        assertThat(loan.getDueDate()).isEqualTo(DUE.plusDays(7));
    }

    @Test
    void shouldRenewUpToTheLimitAndRefuseTheNext() {
        var loan = loan();
        loan.renew(PICKUP.plusDays(1), RENEWAL_LIMIT, PERIOD, false, true);
        loan.renew(PICKUP.plusDays(2), RENEWAL_LIMIT, PERIOD, false, true);

        assertThat(loan.getRenewals()).isEqualTo(2);
        assertThat(loan.getDueDate()).isEqualTo(DUE.plusDays(2L * PERIOD));
        assertThatThrownBy(() -> loan.renew(PICKUP.plusDays(3), RENEWAL_LIMIT, PERIOD, false, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de renovações");
        assertThat(loan.getRenewals()).isEqualTo(2);
        assertThat(loan.getDueDate()).isEqualTo(DUE.plusDays(2L * PERIOD));
    }

    @Test
    void theLimitComesFromTheConfigurationSoZeroMeansNoRenewals() {
        var loan = loan();

        assertThatThrownBy(() -> loan.renew(PICKUP, 0, PERIOD, false, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de renovações");
        loan.renew(PICKUP, 1, PERIOD, false, true); // a bigger limit allows it
        assertThat(loan.getRenewals()).isEqualTo(1);
    }

    @Test
    void shouldRefuseToRenewALateLoanChangingNothing() {
        var loan = loan();

        assertThatThrownBy(() -> loan.renew(DUE.plusDays(1), RENEWAL_LIMIT, PERIOD, false, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("atraso");
        assertThat(loan.getRenewals()).isZero();
        assertThat(loan.getDueDate()).isEqualTo(DUE);
    }

    @Test
    void aRenewalMovesTheDueDateSoTheLoanIsNotLateAnymoreAgainstTheNewOne() {
        var loan = loan();
        loan.renew(DUE, RENEWAL_LIMIT, PERIOD, false, true);

        assertThat(loan.isOverdue(DUE.plusDays(1))).isFalse();
        assertThat(loan.isOverdue(DUE.plusDays(PERIOD + 1))).isTrue();
    }

    @Test
    void shouldRefuseToRenewWhenAReservationWaitsInTheQueueChangingNothing() {
        var loan = loan();

        assertThatThrownBy(() -> loan.renew(PICKUP.plusDays(3), RENEWAL_LIMIT, PERIOD, true, true))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reserva esperando na fila");

        assertThat(loan.getDueDate()).isEqualTo(DUE);
        assertThat(loan.getRenewals()).isZero();
    }

    @Test
    void shouldRefuseToRenewWhenTheBookWasInactivatedChangingNothing() {
        var loan = loan();

        assertThatThrownBy(() -> loan.renew(PICKUP.plusDays(3), RENEWAL_LIMIT, PERIOD, false, false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("o livro está inativo");

        assertThat(loan.getDueDate()).isEqualTo(DUE);
        assertThat(loan.getRenewals()).isZero();
    }

    @Test
    void aLateLoanShouldBeRefusedForBeingLateEvenWhenTheBookIsInactive() {
        var loan = loan();

        assertThatThrownBy(() -> loan.renew(DUE.plusDays(1), RENEWAL_LIMIT, PERIOD, false, false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("em atraso");
    }

    @Test
    void shouldRenewNormallyWhenNoReservationWaitsInTheQueue() {
        var loan = loan();

        loan.renew(PICKUP.plusDays(3), RENEWAL_LIMIT, PERIOD, false, true);

        assertThat(loan.getRenewals()).isEqualTo(1);
    }

    @Test
    void shouldRefuseToRenewAReturnedLoan() {
        var loan = loan();
        loan.returnCopy(DUE);

        assertThatThrownBy(() -> loan.renew(DUE, RENEWAL_LIMIT, PERIOD, false, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("encerrado");
        assertThat(loan.getRenewals()).isZero();
        assertThat(loan.getDueDate()).isEqualTo(DUE);
    }

    @Test
    void aReturnedLoanIsReportedAsReturnedEvenWhenItIsAlsoPastTheLimit() {
        var loan = loan();
        loan.renew(PICKUP, 1, PERIOD, false, true);
        loan.returnCopy(PICKUP.plusDays(2));

        assertThatThrownBy(() -> loan.renew(PICKUP.plusDays(3), 1, PERIOD, false, true))
                .hasMessageContaining("encerrado");
    }

    @Test
    void shouldRefuseABadRenewalArgument() {
        var loan = loan();

        assertThatThrownBy(() -> loan.renew(null, RENEWAL_LIMIT, PERIOD, false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan.renew(PICKUP, -1, PERIOD, false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan.renew(PICKUP, RENEWAL_LIMIT, 0, false, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan.returnCopy(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan.daysLate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loan.isOverdue(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // the condition of the copy on return (RF15)

    @ParameterizedTest
    @EnumSource(ReturnCondition.class)
    void shouldKeepTheConditionTheCopyCameBackIn(ReturnCondition condition) {
        var loan = loan();

        loan.returnCopy(DUE, condition);

        assertThat(loan.getReturnCondition()).isEqualTo(condition);
        assertThat(loan.isReturned()).isTrue();
        assertThat(loan.getReturnDate()).isEqualTo(DUE);
    }

    @Test
    void shouldReturnAsGoodWhenNoConditionIsGiven() {
        var loan = loan();

        loan.returnCopy(DUE);

        assertThat(loan.getReturnCondition()).isEqualTo(ReturnCondition.GOOD);
    }

    @Test
    void anActiveLoanHasNoCondition() {
        assertThat(loan().getReturnCondition()).isNull();
    }

    @Test
    void shouldRefuseAReturnWithoutCondition() {
        var loan = loan();

        assertThatThrownBy(() -> loan.returnCopy(DUE, null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("condição");
        assertThat(loan.isActive()).isTrue();
        assertThat(loan.getReturnCondition()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ReturnCondition.class)
    void theDaysLateDoNotDependOnTheCondition(ReturnCondition condition) {
        var loan = loan();

        var daysLate = loan.returnCopy(DUE.plusDays(3), condition);

        assertThat(daysLate).isEqualTo(3);
        assertThat(loan.daysLate(DUE.plusDays(50))).isEqualTo(3);
    }

    @Test
    void shouldKeepTheFirstConditionWhenTheSecondReturnIsRefused() {
        var loan = loan();
        loan.returnCopy(DUE, ReturnCondition.DAMAGED);

        assertThatThrownBy(() -> loan.returnCopy(DUE.plusDays(1), ReturnCondition.GOOD))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("encerrado");
        assertThat(loan.getReturnCondition()).isEqualTo(ReturnCondition.DAMAGED);
    }

    @Test
    void shouldKeepTheLoanActiveWhenTheReturnIsBeforeThePickup() {
        var loan = loan();

        assertThatThrownBy(() -> loan.returnCopy(PICKUP.minusDays(1), ReturnCondition.LOST))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(loan.getReturnCondition()).isNull();
    }

    @Test
    void shouldReconstituteTheConditionOfAReturnedLoan() {
        var loan = Loan.reconstitute(5L, 10L, 20L, PICKUP, DUE, DUE, 0, ReturnCondition.LOST);

        assertThat(loan.getReturnCondition()).isEqualTo(ReturnCondition.LOST);
    }

    @Test
    void shouldReconstituteALoanReturnedWithoutConditionAsGood() {
        var loan = Loan.reconstitute(5L, 10L, 20L, PICKUP, DUE, DUE, 0);

        assertThat(loan.getReturnCondition()).isEqualTo(ReturnCondition.GOOD);
    }

    @Test
    void shouldRefuseAReturnedLoanWithoutConditionAndAnActiveOneWithCondition() {
        assertThatThrownBy(() -> Loan.reconstitute(5L, 10L, 20L, PICKUP, DUE, DUE, 0, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("condição");
        assertThatThrownBy(() -> Loan.reconstitute(5L, 10L, 20L, PICKUP, DUE, null, 0, ReturnCondition.GOOD))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("condição");
    }

    // reconstitution

    @Test
    void shouldReconstituteALoanWithItsPersistedState() {
        var loan = Loan.reconstitute(5L, 10L, 20L, PICKUP, DUE.plusDays(PERIOD), DUE.plusDays(3), 1);

        assertThat(loan.getId()).isEqualTo(5L);
        assertThat(loan.getCopyId()).isEqualTo(10L);
        assertThat(loan.getReaderId()).isEqualTo(20L);
        assertThat(loan.getDueDate()).isEqualTo(DUE.plusDays(PERIOD));
        assertThat(loan.getReturnDate()).isEqualTo(DUE.plusDays(3));
        assertThat(loan.getRenewals()).isEqualTo(1);
        assertThat(loan.isReturned()).isTrue();
    }

    @Test
    void shouldRefuseAnInconsistentPersistedState() {
        assertThatThrownBy(() -> Loan.reconstitute(5L, 10L, 20L, PICKUP, DUE, null, -1))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Loan.reconstitute(5L, 10L, 20L, PICKUP, PICKUP.minusDays(1), null, 0))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Loan.reconstitute(5L, 10L, 20L, PICKUP, DUE, PICKUP.minusDays(1), 0))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Loan.reconstitute(5L, null, 20L, PICKUP, DUE, null, 0))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Loan.reconstitute(5L, 10L, 20L, null, DUE, null, 0))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Loan.reconstitute(5L, 10L, 20L, PICKUP, null, null, 0))
                .isInstanceOf(BusinessRuleException.class);
    }
}
