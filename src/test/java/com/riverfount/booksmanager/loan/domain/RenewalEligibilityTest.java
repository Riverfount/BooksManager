package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

/** Who may renew a loan, by the state of the reader (RN21): each state alone, all together, and none. */
class RenewalEligibilityTest {

    private static RenewalSituation standing() {
        return new RenewalSituation(true, false, false, false);
    }

    @Test
    void shouldAllowAReaderInGoodStanding() {
        assertThat(RenewalEligibility.refusals(standing())).isEmpty();
        assertThatCode(() -> RenewalEligibility.check(standing())).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseAnInactiveReader() {
        assertThat(RenewalEligibility.refusals(new RenewalSituation(false, false, false, false)))
                .containsExactly("o leitor está inativo");
    }

    @Test
    void shouldRefuseABlockedReader() {
        assertThat(RenewalEligibility.refusals(new RenewalSituation(true, true, false, false)))
                .containsExactly("o leitor está bloqueado");
    }

    @Test
    void shouldRefuseAReaderWithAPendingFine() {
        assertThat(RenewalEligibility.refusals(new RenewalSituation(true, false, true, false)))
                .containsExactly("o leitor tem multa pendente");
    }

    @Test
    void shouldRefuseASuspendedReader() {
        assertThat(RenewalEligibility.refusals(new RenewalSituation(true, false, false, true)))
                .containsExactly("o leitor está suspenso");
    }

    @Test
    void shouldReportEveryReasonAtOnceInAStableOrder() {
        var all = new RenewalSituation(false, true, true, true);

        assertThat(RenewalEligibility.refusals(all)).containsExactly("o leitor está inativo",
                "o leitor está bloqueado", "o leitor tem multa pendente", "o leitor está suspenso");
        assertThatThrownBy(() -> RenewalEligibility.check(all)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("A renovação não pode ser feita: o leitor está inativo; o leitor está bloqueado; "
                        + "o leitor tem multa pendente; o leitor está suspenso.");
    }

    @Test
    void shouldRequireTheSituation() {
        assertThatThrownBy(() -> RenewalEligibility.refusals(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
