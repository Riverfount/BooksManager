package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Whether a reader may take a copy (RF11, HU07): one test per rule, each refusing for its own reason,
 * with the facts given by parameter and the loan limit coming from the configuration (RN16).
 */
class BorrowingEligibilityTest {

    private static final int LIMIT = 3;

    /** A reader with nothing against them, borrowing an available copy. */
    private static Builder ok() {
        return new Builder();
    }

    private static final class Builder {
        private boolean copyAvailable = true;
        private CopyHold copyHold = CopyHold.NONE;
        private boolean bookActive = true;
        private boolean readerActive = true;
        private boolean readerBlocked = false;
        private int activeLoans = 0;
        private boolean pendingFine = false;
        private boolean suspended = false;
        private boolean overdue = false;

        Builder copyUnavailable() {
            copyAvailable = false;
            return this;
        }

        Builder copyHold(CopyHold hold) {
            copyHold = hold;
            copyAvailable = false; // a kept copy is never "available"
            return this;
        }

        Builder inactiveBook() {
            bookActive = false;
            return this;
        }

        Builder inactiveReader() {
            readerActive = false;
            return this;
        }

        Builder blockedReader() {
            readerBlocked = true;
            return this;
        }

        Builder activeLoans(int count) {
            activeLoans = count;
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

        BorrowingSituation build() {
            return new BorrowingSituation(copyAvailable, copyHold, bookActive, readerActive, readerBlocked, activeLoans,
                    pendingFine, suspended, overdue);
        }
    }

    @Test
    void shouldRequireTheHoldOnTheCopy() {
        assertThatThrownBy(() -> new BorrowingSituation(true, null, true, true, false, 0, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // an inactive book (RN14)

    @Test
    void shouldRefuseALoanOfACopyOfAnInactiveBook() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().inactiveBook().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("o livro está inativo");
    }

    @Test
    void shouldRefuseTheOwnerOfTheReservationToPickUpACopyOfAnInactiveBook() {
        var situation = ok().copyHold(CopyHold.FOR_THIS_READER).inactiveBook().build();

        assertThat(BorrowingEligibility.refusals(situation, LIMIT)).containsExactly("o livro está inativo");
    }

    @Test
    void shouldReportTheInactiveBookTogetherWithTheOtherReasons() {
        var reasons = BorrowingEligibility.refusals(ok().inactiveBook().inactiveReader().pendingFine().build(), LIMIT);

        assertThat(reasons).containsExactly("o livro está inativo", "o leitor está inativo",
                "o leitor tem multa pendente");
    }

    @Test
    void shouldNotMentionTheBookWhenItIsActive() {
        assertThat(BorrowingEligibility.refusals(ok().copyUnavailable().build(), LIMIT))
                .noneMatch(reason -> reason.contains("livro"));
    }

    // allowed

    @Test
    void shouldAllowAReaderWithNothingAgainstThem() {
        assertThatCode(() -> BorrowingEligibility.check(ok().build(), LIMIT)).doesNotThrowAnyException();
        assertThat(BorrowingEligibility.refusals(ok().build(), LIMIT)).isEmpty();
    }

    @Test
    void shouldAllowAReaderWithLoansBelowTheLimit() {
        assertThatCode(() -> BorrowingEligibility.check(ok().activeLoans(LIMIT - 1).build(), LIMIT))
                .doesNotThrowAnyException();
    }

    // RN03: only an available copy

    @Test
    void shouldRefuseACopyThatIsNotAvailable() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().copyUnavailable().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar não está disponível");
    }

    // RN03 + RN13: a copy kept for a reservation goes only to its owner, within the deadline

    @Test
    void theOwnerOfTheReservationShouldTakeTheCopyKeptForThem() {
        assertThatCode(() -> BorrowingEligibility.check(ok().copyHold(CopyHold.FOR_THIS_READER).build(), LIMIT))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseACopyKeptForAnotherReader() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().copyHold(CopyHold.FOR_ANOTHER_READER).build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar reservado para outro leitor")
                .hasMessageNotContaining("não está disponível");
    }

    @Test
    void shouldRefuseTheOwnerAfterThePickupDeadline() {
        assertThatThrownBy(() -> BorrowingEligibility.check(
                ok().copyHold(CopyHold.FOR_THIS_READER_PAST_DEADLINE).build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prazo de retirada da reserva deste exemplar já terminou");
    }

    @Test
    void theOwnerStillHasToMeetTheOtherRules() {
        var reasons = BorrowingEligibility.refusals(ok().copyHold(CopyHold.FOR_THIS_READER).pendingFine()
                .suspended().overdueLoan().activeLoans(LIMIT).blockedReader().build(), LIMIT);

        assertThat(reasons).hasSize(5).noneMatch(reason -> reason.contains("exemplar"));
    }

    @Test
    void shouldReportTheHoldTogetherWithTheOtherReasons() {
        var reasons = BorrowingEligibility.refusals(
                ok().copyHold(CopyHold.FOR_ANOTHER_READER).pendingFine().build(), LIMIT);

        assertThat(reasons).containsExactly("exemplar reservado para outro leitor", "o leitor tem multa pendente");
    }

    // RN11: an inactive or blocked reader

    @Test
    void shouldRefuseAnInactiveReader() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().inactiveReader().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("leitor está inativo");
    }

    @Test
    void shouldRefuseABlockedReader() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().blockedReader().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("leitor está bloqueado");
    }

    // RN04: the limit of active loans, from the configuration

    @Test
    void shouldRefuseAReaderWhoReachedTheLimit() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().activeLoans(LIMIT).build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de " + LIMIT + " empréstimos");
    }

    @Test
    void shouldRefuseAReaderAboveTheLimitToo() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().activeLoans(LIMIT + 2).build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void theLimitIsTheOneGivenNotAConstant() {
        var fiveLoans = ok().activeLoans(5).build();

        assertThatThrownBy(() -> BorrowingEligibility.check(fiveLoans, 5))
                .hasMessageContaining("limite de 5 empréstimos");
        assertThatCode(() -> BorrowingEligibility.check(fiveLoans, 6)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().activeLoans(1).build(), 1))
                .hasMessageContaining("limite de 1 empréstimo");
    }

    // RN05: a pending fine, a suspension in force and a late loan

    @Test
    void shouldRefuseAReaderWithAPendingFine() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().pendingFine().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("multa pendente");
    }

    @Test
    void shouldRefuseASuspendedReader() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().suspended().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("suspenso");
    }

    @Test
    void shouldRefuseAReaderWithALateLoan() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().overdueLoan().build(), LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo em atraso");
    }

    // each rule is the only reason when it is the only problem

    @Test
    void eachRuleRefusesForItsOwnReasonOnly() {
        assertThat(BorrowingEligibility.refusals(ok().copyUnavailable().build(), LIMIT)).hasSize(1);
        assertThat(BorrowingEligibility.refusals(ok().inactiveReader().build(), LIMIT)).hasSize(1);
        assertThat(BorrowingEligibility.refusals(ok().blockedReader().build(), LIMIT)).hasSize(1);
        assertThat(BorrowingEligibility.refusals(ok().activeLoans(LIMIT).build(), LIMIT)).hasSize(1);
        assertThat(BorrowingEligibility.refusals(ok().pendingFine().build(), LIMIT)).hasSize(1);
        assertThat(BorrowingEligibility.refusals(ok().suspended().build(), LIMIT)).hasSize(1);
        assertThat(BorrowingEligibility.refusals(ok().overdueLoan().build(), LIMIT)).hasSize(1);
    }

    // several problems: all of them are told, so the operator solves them in one go

    @Test
    void shouldTellEveryReasonWhenThereIsMoreThanOne() {
        var situation = ok().pendingFine().suspended().overdueLoan().activeLoans(LIMIT).build();

        var reasons = BorrowingEligibility.refusals(situation, LIMIT);

        assertThat(reasons).hasSize(4)
                .anyMatch(r -> r.contains("multa pendente"))
                .anyMatch(r -> r.contains("suspenso"))
                .anyMatch(r -> r.contains("empréstimo em atraso"))
                .anyMatch(r -> r.contains("limite"));
        assertThatThrownBy(() -> BorrowingEligibility.check(situation, LIMIT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("multa pendente")
                .hasMessageContaining("suspenso")
                .hasMessageContaining("empréstimo em atraso")
                .hasMessageContaining("limite");
    }

    @Test
    void anInactiveAndBlockedReaderGetsBothReasons() {
        var reasons = BorrowingEligibility.refusals(ok().inactiveReader().blockedReader().build(), LIMIT);

        assertThat(reasons).hasSize(2);
    }

    @Test
    void shouldKeepTheReasonsInAStableOrderStartingFromTheCopyAndTheReader() {
        var reasons = BorrowingEligibility.refusals(ok().copyUnavailable().inactiveReader().pendingFine().build(),
                LIMIT);

        assertThat(reasons.get(0)).contains("exemplar");
        assertThat(reasons.get(1)).contains("inativo");
        assertThat(reasons.get(2)).contains("multa pendente");
    }

    @Test
    void theMessageNeverCarriesAnythingButTheReasons() {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().pendingFine().build(), LIMIT))
                .hasMessage("O empréstimo não pode ser feito: o leitor tem multa pendente.");
    }

    // bad arguments

    @Test
    void shouldRequireTheSituation() {
        assertThatThrownBy(() -> BorrowingEligibility.check(null, LIMIT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BorrowingEligibility.refusals(null, LIMIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aLimitBelowOneIsAProgrammingErrorNotARefusal(int limit) {
        assertThatThrownBy(() -> BorrowingEligibility.check(ok().build(), limit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativeCountOfLoansIsAProgrammingError() {
        assertThatThrownBy(() -> ok().activeLoans(-1).build()).isInstanceOf(IllegalArgumentException.class);
    }
}
