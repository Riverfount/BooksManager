package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules that decide whether a reader may take a copy (RF11, HU07), applied to the facts of
 * a {@link BorrowingSituation}. Nothing here is looked up or read from the clock, and no number
 * is a constant: the limit of simultaneous loans is the library's, from its configuration (RN16).
 *
 * <p>A loan is refused for:
 * <ul>
 *   <li>a copy of a book that was inactivated (RN14), even the one kept for the reservation of the reader;</li>
 *   <li>a copy that is not available (RN03), or that is kept for a reservation the reader does not own, or
 *       whose pickup deadline has passed (RN13);</li>
 *   <li>a reader who is inactive or blocked (RN11);</li>
 *   <li>a reader who already has as many open loans as the limit (RN04);</li>
 *   <li>a reader with a pending fine, a suspension in force or a late loan (RN05).</li>
 * </ul>
 * Every rule that fails is reported, in a stable order, so the operator sees all the reasons at
 * once instead of fixing one and meeting the next.
 */
public final class BorrowingEligibility {

    private BorrowingEligibility() {
    }

    /**
     * @param loanLimit how many simultaneous loans a reader may have, from the configuration
     * @throws BusinessRuleException with every reason when the loan is not allowed
     */
    public static void check(BorrowingSituation situation, int loanLimit) {
        var reasons = refusals(situation, loanLimit);
        if (!reasons.isEmpty()) {
            throw new BusinessRuleException("O empréstimo não pode ser feito: " + String.join("; ", reasons) + ".");
        }
    }

    /** The reasons the loan is not allowed, in a stable order; empty when it is allowed. */
    public static List<String> refusals(BorrowingSituation situation, int loanLimit) {
        if (situation == null) {
            throw new IllegalArgumentException("the borrowing situation is required");
        }
        if (loanLimit < 1) {
            throw new IllegalArgumentException("the loan limit must be at least one");
        }
        var reasons = new ArrayList<String>();
        if (!situation.bookActive()) {
            reasons.add("o livro está inativo");
        }
        switch (situation.copyHold()) {
            case NONE -> {
                if (!situation.copyAvailable()) {
                    reasons.add("o exemplar não está disponível para empréstimo");
                }
            }
            case FOR_THIS_READER -> {
                // the owner of the reservation takes the copy kept for them: the exception of RN03
            }
            case FOR_THIS_READER_PAST_DEADLINE ->
                    reasons.add("o prazo de retirada da reserva deste exemplar já terminou");
            case FOR_ANOTHER_READER -> reasons.add("exemplar reservado para outro leitor");
        }
        if (!situation.readerActive()) {
            reasons.add("o leitor está inativo");
        }
        if (situation.readerBlocked()) {
            reasons.add("o leitor está bloqueado");
        }
        if (situation.activeLoans() >= loanLimit) {
            reasons.add("o leitor já atingiu o limite de " + loanLimit
                    + (loanLimit == 1 ? " empréstimo simultâneo" : " empréstimos simultâneos"));
        }
        if (situation.hasPendingFine()) {
            reasons.add("o leitor tem multa pendente");
        }
        if (situation.hasSuspensionInForce()) {
            reasons.add("o leitor está suspenso");
        }
        if (situation.hasOverdueLoan()) {
            reasons.add("o leitor tem empréstimo em atraso");
        }
        return List.copyOf(reasons);
    }
}
