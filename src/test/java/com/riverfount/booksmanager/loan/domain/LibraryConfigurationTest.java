package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The rules of the library as the domain sees them (RF26, RN16, RN08). */
class LibraryConfigurationTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /** The values the first migration creates (decided on 10/10/2026). */
    private static LibraryConfiguration initial() {
        return LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE, money("1.00"), money("10.00"), 1, 365, 14,
                3, 2, 3, 2, "PAT");
    }

    private static LibraryConfiguration with(PenaltyPolicy policy, String dailyFine, String fineCap, int daysPerDay,
            int suspensionCap, int period, int loanLimit, int renewalLimit, int pickupDays) {
        return withNotice(policy, dailyFine, fineCap, daysPerDay, suspensionCap, period, loanLimit, renewalLimit,
                pickupDays, 2);
    }

    private static LibraryConfiguration withNotice(PenaltyPolicy policy, String dailyFine, String fineCap,
            int daysPerDay, int suspensionCap, int period, int loanLimit, int renewalLimit, int pickupDays,
            int dueSoonNoticeDays) {
        return LibraryConfiguration.reconstitute(1L, policy, money(dailyFine), money(fineCap), daysPerDay,
                suspensionCap, period, loanLimit, renewalLimit, pickupDays, dueSoonNoticeDays, "PAT");
    }

    // reconstitution

    @Test
    void shouldExposeEveryValueItWasBuiltWith() {
        var configuration = initial();

        assertThat(configuration.getId()).isEqualTo(1L);
        assertThat(configuration.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.FINE);
        assertThat(configuration.getDailyFineAmount()).isEqualByComparingTo("1.00");
        assertThat(configuration.getFineCap()).isEqualByComparingTo("10.00");
        assertThat(configuration.getSuspensionDaysPerDay()).isEqualTo(1);
        assertThat(configuration.getSuspensionCapDays()).isEqualTo(365);
        assertThat(configuration.getLoanPeriodDays()).isEqualTo(14);
        assertThat(configuration.getLoanLimit()).isEqualTo(3);
        assertThat(configuration.getRenewalLimit()).isEqualTo(2);
        assertThat(configuration.getReservationPickupDays()).isEqualTo(3);
    }

    @Test
    void shouldAcceptAnyPolicyVariant() {
        for (var policy : PenaltyPolicy.values()) {
            assertThat(with(policy, "1.00", "10.00", 1, 365, 14, 3, 2, 3).getPenaltyPolicy()).isEqualTo(policy);
        }
    }

    @Test
    void shouldAcceptZeroDailyFineAndZeroRenewalsWhichMeanSomething() {
        var configuration = with(PenaltyPolicy.SUSPENSION, "0", "10.00", 1, 365, 14, 3, 0, 3);

        assertThat(configuration.getDailyFineAmount()).isEqualByComparingTo("0");
        assertThat(configuration.getRenewalLimit()).isZero();
    }

    @Test
    void shouldRequireThePolicyAndEveryNumber() {
        assertThatThrownBy(() -> LibraryConfiguration.reconstitute(1L, null, money("1.00"), money("10.00"), 1, 365,
                14, 3, 2, 3, 2, "PAT")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("política");
        assertThatThrownBy(() -> LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE, null, money("10.00"), 1,
                365, 14, 3, 2, 3, 2, "PAT")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("valor diário");
        assertThatThrownBy(() -> LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE, money("1.00"), null, 1,
                365, 14, 3, 2, 3, 2, "PAT")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("teto da multa");
    }

    // the rules (RN16), one by one

    @Test
    void theDailyFineCannotBeNegative() {
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "-0.01", "10.00", 1, 365, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("valor diário da multa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.00"})
    void theFineCapMustBePositive(String cap) {
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "0", cap, 1, 365, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("teto da multa");
    }

    @Test
    void theFineCapCannotBeBelowOneDayOfFine() {
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "5.00", "4.99", 1, 365, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("teto da multa")
                .hasMessageContaining("valor diário");
    }

    @Test
    void theFineCapMayEqualOneDayOfFine() {
        assertThat(with(PenaltyPolicy.FINE, "5.00", "5.00", 1, 365, 14, 3, 2, 3).getFineCap())
                .isEqualByComparingTo("5.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.001", "0.005", "10.999"})
    void theMoneyCannotHaveMoreThanTwoDecimalPlaces(String amount) {
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, amount, "20.00", 1, 365, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("duas casas");
    }

    @Test
    void theMoneyCannotBeBiggerThanTheColumnHolds() {
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "1.00", "100000000.00", 1, 365, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("teto da multa");
    }

    @Test
    void theMoneyIsAlwaysKeptWithTwoDecimalPlacesWhateverScaleItArrivedWith() {
        var built = with(PenaltyPolicy.FINE, "1.5", "20", 1, 365, 14, 3, 2, 3);
        assertThat(built.getDailyFineAmount().toPlainString()).isEqualTo("1.50");
        assertThat(built.getFineCap().toPlainString()).isEqualTo("20.00");

        var configuration = initial();
        configuration.update(null, money("2.500"), money("3E+1"), null, null, null, null, null, null, null, null);
        assertThat(configuration.getDailyFineAmount().toPlainString()).isEqualTo("2.50");
        assertThat(configuration.getFineCap().toPlainString()).isEqualTo("30.00");
    }

    @Test
    void aHugeExponentIsRefusedQuicklyAndNeverExpanded() {
        for (var huge : new String[] {"1E+999999999", "1E+2147483647", "9.99E+999999999"}) {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(
                    () -> with(PenaltyPolicy.FINE, "1.00", huge, 1, 365, 14, 3, 2, 3))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("teto da multa"));
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(
                    () -> with(PenaltyPolicy.FINE, huge, "10.00", 1, 365, 14, 3, 2, 3))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("valor diário"));
        }
        // a tiny one is refused for its decimal places, also without expanding it
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(
                () -> with(PenaltyPolicy.FINE, "1E-999999999", "10.00", 1, 365, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("duas casas"));
    }

    @Test
    void trailingZerosInTheMoneyAreNotExtraDecimalPlaces() {
        assertThat(with(PenaltyPolicy.FINE, "1.500", "10.0000", 1, 365, 14, 3, 2, 3).getDailyFineAmount())
                .isEqualByComparingTo("1.5");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void theSuspensionFactorAndCapMustBeAtLeastOne(int value) {
        assertThatThrownBy(() -> with(PenaltyPolicy.SUSPENSION, "1.00", "10.00", value, 365, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dias de suspensão por dia de atraso");
        assertThatThrownBy(() -> with(PenaltyPolicy.SUSPENSION, "1.00", "10.00", 1, value, 14, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("teto da suspensão");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void theLoanPeriodTheLoanLimitAndThePickupPeriodMustBeAtLeastOne(int value) {
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "1.00", "10.00", 1, 365, value, 3, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prazo de empréstimo");
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "1.00", "10.00", 1, 365, 14, value, 2, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de empréstimos");
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "1.00", "10.00", 1, 365, 14, 3, 2, value))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prazo de retirada");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void theDaysOfNoticeBeforeTheDueDateMustBeAtLeastOne(int value) {
        assertThatThrownBy(() -> withNotice(PenaltyPolicy.FINE, "1.00", "10.00", 1, 365, 14, 3, 2, 3, value))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("antecedência do aviso de vencimento");
    }

    @Test
    void shouldRefuseANewDaysOfNoticeBelowOneAndKeepTheCurrentOne() {
        var configuration = initial();

        assertThatThrownBy(() -> configuration.update(null, null, null, null, null, null, null, null, null, 0, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("antecedência do aviso de vencimento");
        assertThat(configuration.getDueSoonNoticeDays()).isEqualTo(2);
    }

    @Test
    void theRenewalLimitCannotBeNegative() {
        assertThatThrownBy(() -> with(PenaltyPolicy.FINE, "1.00", "10.00", 1, 365, 14, 3, -1, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de renovações");
    }

    // partial update (the PATCH contract: an omitted or null value keeps the current one)

    @Test
    void shouldChangeOnlyTheValuesInformed() {
        var configuration = initial();

        configuration.update(PenaltyPolicy.BOTH, null, null, null, null, 21, null, null, null, null, null);

        assertThat(configuration.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.BOTH);
        assertThat(configuration.getLoanPeriodDays()).isEqualTo(21);
        assertThat(configuration.getDailyFineAmount()).isEqualByComparingTo("1.00");
        assertThat(configuration.getFineCap()).isEqualByComparingTo("10.00");
        assertThat(configuration.getSuspensionDaysPerDay()).isEqualTo(1);
        assertThat(configuration.getSuspensionCapDays()).isEqualTo(365);
        assertThat(configuration.getLoanLimit()).isEqualTo(3);
        assertThat(configuration.getRenewalLimit()).isEqualTo(2);
        assertThat(configuration.getReservationPickupDays()).isEqualTo(3);
    }

    @Test
    void shouldChangeEveryValueWhenAllAreInformed() {
        var configuration = initial();

        configuration.update(PenaltyPolicy.SUSPENSION, money("2.50"), money("30.00"), 2, 90, 7, 5, 0, 1, 4, null);

        assertThat(configuration.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.SUSPENSION);
        assertThat(configuration.getDailyFineAmount()).isEqualByComparingTo("2.50");
        assertThat(configuration.getFineCap()).isEqualByComparingTo("30.00");
        assertThat(configuration.getSuspensionDaysPerDay()).isEqualTo(2);
        assertThat(configuration.getSuspensionCapDays()).isEqualTo(90);
        assertThat(configuration.getLoanPeriodDays()).isEqualTo(7);
        assertThat(configuration.getLoanLimit()).isEqualTo(5);
        assertThat(configuration.getRenewalLimit()).isZero();
        assertThat(configuration.getReservationPickupDays()).isEqualTo(1);
        assertThat(configuration.getDueSoonNoticeDays()).isEqualTo(4);
    }

    @Test
    void updatingWithNothingInformedChangesNothing() {
        var configuration = initial();

        configuration.update(null, null, null, null, null, null, null, null, null, null, null);

        assertThat(configuration).usingRecursiveComparison().isEqualTo(initial());
    }

    @Test
    void shouldChangeNothingWhenAnyValueIsInvalid() {
        var configuration = initial();

        // the first values are fine; the last one (a zero loan limit) is not
        assertThatThrownBy(() -> configuration.update(PenaltyPolicy.NONE, money("2.00"), money("20.00"), 3, 30, 10,
                0, 1, 2, 3, null)).isInstanceOf(BusinessRuleException.class);

        assertThat(configuration).usingRecursiveComparison().isEqualTo(initial());
    }

    @Test
    void theRulesAlsoCoverTheValuesKeptNotOnlyTheOnesInformed() {
        var configuration = initial();

        // the cap stays at 10.00, so a daily fine of 20.00 would exceed it
        assertThatThrownBy(() -> configuration.update(null, money("20.00"), null, null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("teto da multa");
        assertThat(configuration.getDailyFineAmount()).isEqualByComparingTo("1.00");

        // raising both together is fine
        configuration.update(null, money("20.00"), money("50.00"), null, null, null, null, null, null, null, null);
        assertThat(configuration.getDailyFineAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldNotChangeTheId() {
        var configuration = initial();

        configuration.update(PenaltyPolicy.NONE, null, null, null, null, null, null, null, null, null, null);

        assertThat(configuration.getId()).isEqualTo(1L);
    }

    // the asset code prefix (RF26, RN16)

    @Test
    void shouldExposeTheAssetCodePrefix() {
        assertThat(initial().getAssetCodePrefix()).isEqualTo("PAT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BIB", "ABCD", "esc", "Bib"})
    void shouldAcceptAPrefixOfThreeOrFourLettersKeepingItUppercase(String prefix) {
        var configuration = initial();

        configuration.update(null, null, null, null, null, null, null, null, null, null, prefix);

        assertThat(configuration.getAssetCodePrefix()).isEqualTo(prefix.toUpperCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PA", "PATRI", "PA1", "PA-", "PÁT", " PAT", "PA T", ""})
    void shouldRefuseAPrefixThatIsNotThreeOrFourLettersAndKeepTheCurrentOne(String prefix) {
        var configuration = initial();

        assertThatThrownBy(() -> configuration.update(null, null, null, null, null, null, null, null, null, null,
                prefix)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("prefixo do código de patrimônio");

        assertThat(configuration.getAssetCodePrefix()).isEqualTo("PAT");
    }

    @Test
    void shouldKeepThePrefixWhenTheUpdateDoesNotSendOne() {
        var configuration = initial();

        configuration.update(PenaltyPolicy.NONE, null, null, null, null, null, null, null, null, null, null);

        assertThat(configuration.getAssetCodePrefix()).isEqualTo("PAT");
    }

    @Test
    void shouldNotChangeAnythingWhenThePrefixIsInvalidEvenIfTheOtherValuesAreFine() {
        var configuration = initial();

        assertThatThrownBy(() -> configuration.update(PenaltyPolicy.NONE, null, null, null, null, 30, null, null,
                null, null, "AB")).isInstanceOf(BusinessRuleException.class);

        assertThat(configuration).usingRecursiveComparison().isEqualTo(initial());
    }

    @Test
    void shouldRequireThePrefixOnReconstitution() {
        assertThatThrownBy(() -> LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE, money("1.00"),
                money("10.00"), 1, 365, 14, 3, 2, 3, 2, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("prefixo do código de patrimônio");
    }
}
