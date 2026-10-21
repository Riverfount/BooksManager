package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Whether a reader may reserve a book (RF16, RN12): one test per rule, each refusing for its own
 * reason, with the facts given by parameter and the limit coming from the configuration (RN16).
 * The limit of loans and the availability of a copy are not rules of reserving, beyond RN12.
 */
class ReservationEligibilityTest {

    private static final int LIMIT = 3;

    /** A reader with nothing against them, reserving a book with no copy available. */
    private static Builder ok() {
        return new Builder();
    }

    private static final class Builder {
        private boolean readerActive = true;
        private boolean readerBlocked = false;
        private int activeReservations = 0;
        private boolean duplicate = false;
        private boolean availableCopy = false;
        private boolean onLoan = false;
        private boolean pendingFine = false;
        private boolean suspended = false;
        private boolean overdue = false;

        Builder inactiveReader() {
            readerActive = false;
            return this;
        }

        Builder blockedReader() {
            readerBlocked = true;
            return this;
        }

        Builder activeReservations(int count) {
            activeReservations = count;
            return this;
        }

        Builder reservedAlready() {
            duplicate = true;
            return this;
        }

        Builder bookWithAvailableCopy() {
            availableCopy = true;
            return this;
        }

        Builder bookOnLoan() {
            onLoan = true;
            return this;
        }

        Builder pendingFine() {
            pendingFine = true;
            return this;
        }

        Builder suspended() {
            suspended = true;
            return this;
        }

        Builder overdueLoan() {
            overdue = true;
            return this;
        }

        ReservationSituation build() {
            return new ReservationSituation(readerActive, readerBlocked, activeReservations, duplicate, availableCopy,
                    onLoan, pendingFine, suspended, overdue);
        }
    }

    // allowed

    @Test
    void shouldAllowAReaderWithNothingAgainstThem() {
        assertThatCode(() -> ReservationEligibility.check(ok().build(), LIMIT)).doesNotThrowAnyException();
        assertThat(ReservationEligibility.refusals(ok().build(), LIMIT)).isEmpty();
    }

    @Test
    void shouldAllowAReaderWithReservationsBelowTheLimit() {
        assertThatCode(() -> ReservationEligibility.check(ok().activeReservations(LIMIT - 1).build(), LIMIT))
                .doesNotThrowAnyException();
    }

    // RN11: an inactive or blocked reader

    @Test
    void shouldRefuseAnInactiveReader() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().inactiveReader().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("leitor está inativo");
    }

    @Test
    void shouldRefuseABlockedReader() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().blockedReader().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("leitor está bloqueado");
    }

    // RN12: a book with a copy available is a case for lending, not for reserving

    @Test
    void shouldRefuseABookWithAnAvailableCopy() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().bookWithAvailableCopy().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar disponível")
                .hasMessageContaining("empréstimo");
    }

    // RN12: a book the reader already has on loan

    @Test
    void shouldRefuseABookTheReaderAlreadyHasOnLoan() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().bookOnLoan().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já tem este livro emprestado");
    }

    // RN12: the same book twice

    @Test
    void shouldRefuseTheSameBookTwice() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().reservedAlready().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já tem uma reserva ativa deste livro");
    }

    // RN12, RN04: the limit of active reservations is the loan limit, counted apart from the loans

    @Test
    void shouldRefuseAReaderWhoReachedTheLimit() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().activeReservations(LIMIT).build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de " + LIMIT + " reservas ativas");
    }

    @Test
    void shouldRefuseAReaderAboveTheLimitToo() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().activeReservations(LIMIT + 2).build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void theLimitIsTheOneGivenNotAConstant() {
        var five = ok().activeReservations(5).build();

        assertThatThrownBy(() -> ReservationEligibility.check(five, 5))
                .hasMessageContaining("limite de 5 reservas ativas");
        assertThatCode(() -> ReservationEligibility.check(five, 6)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ReservationEligibility.check(ok().activeReservations(1).build(), 1))
                .hasMessageContaining("limite de 1 reserva ativa");
    }

    // RN05: a pending fine, a suspension in force and a late loan

    @Test
    void shouldRefuseAReaderWithAPendingFine() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().pendingFine().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("multa pendente");
    }

    @Test
    void shouldRefuseASuspendedReader() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().suspended().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("suspenso");
    }

    @Test
    void shouldRefuseAReaderWithALateLoan() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().overdueLoan().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo em atraso");
    }

    // each rule is the only reason when it is the only problem

    @Test
    void eachRuleRefusesForItsOwnReasonOnly() {
        assertThat(ReservationEligibility.refusals(ok().inactiveReader().build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().blockedReader().build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().bookWithAvailableCopy().build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().bookOnLoan().build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().reservedAlready().build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().activeReservations(LIMIT).build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().pendingFine().build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().suspended().build(), LIMIT)).hasSize(1);
        assertThat(ReservationEligibility.refusals(ok().overdueLoan().build(), LIMIT)).hasSize(1);
    }

    // several problems: all of them are told, in a stable order

    @Test
    void shouldTellEveryReasonWhenEverythingFails() {
        var situation = ok().inactiveReader().blockedReader().bookWithAvailableCopy().bookOnLoan().reservedAlready()
                .activeReservations(LIMIT).pendingFine().suspended().overdueLoan().build();

        var reasons = ReservationEligibility.refusals(situation, LIMIT);

        assertThat(reasons).hasSize(9);
        assertThat(reasons.get(0)).contains("inativo");
        assertThat(reasons.get(1)).contains("bloqueado");
        assertThat(reasons.get(2)).contains("exemplar disponível");
        assertThat(reasons.get(3)).contains("emprestado");
        assertThat(reasons.get(4)).contains("reserva ativa deste livro");
        assertThat(reasons.get(5)).contains("limite");
        assertThat(reasons.get(6)).contains("multa pendente");
        assertThat(reasons.get(7)).contains("suspenso");
        assertThat(reasons.get(8)).contains("empréstimo em atraso");
        assertThatThrownBy(() -> ReservationEligibility.check(situation, LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("A reserva não pode ser feita: ")
                .hasMessageContaining("inativo")
                .hasMessageContaining("empréstimo em atraso");
    }

    @Test
    void shouldTellSeveralReasonsAtOnce() {
        var reasons = ReservationEligibility.refusals(ok().pendingFine().suspended().overdueLoan().build(), LIMIT);

        assertThat(reasons).hasSize(3);
    }

    @Test
    void theMessageNeverCarriesAnythingButTheReasons() {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().pendingFine().build(), LIMIT))
                .hasMessage("A reserva não pode ser feita: o leitor tem multa pendente.");
    }

    // bad arguments

    @Test
    void shouldRequireTheSituation() {
        assertThatThrownBy(() -> ReservationEligibility.check(null, LIMIT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationEligibility.refusals(null, LIMIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aLimitBelowOneIsAProgrammingErrorNotARefusal(int limit) {
        assertThatThrownBy(() -> ReservationEligibility.check(ok().build(), limit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativeCountOfReservationsIsAProgrammingError() {
        assertThatThrownBy(() -> ok().activeReservations(-1).build()).isInstanceOf(IllegalArgumentException.class);
    }
}
