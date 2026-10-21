package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules that decide whether a reader may reserve a book (RF16), applied to the facts of a
 * {@link ReservationSituation}. Only who could borrow may reserve, except the availability of a
 * copy and the limit of loans. Nothing here is looked up or read from the clock, and no number is
 * a constant: the limit of active reservations is the loan limit of the library configuration,
 * counted apart from the loans (RN04, RN12, RN16).
 *
 * <p>A reservation is refused for:
 * <ul>
 *   <li>a reader who is inactive or blocked (RN11);</li>
 *   <li>a book with a copy available, which is a case for lending (RN12);</li>
 *   <li>a book the reader already has on loan, or already reserved (RN12);</li>
 *   <li>a reader who already has as many active reservations as the limit (RN12);</li>
 *   <li>a reader with a pending fine, a suspension in force or a late loan (RN05).</li>
 * </ul>
 * Every rule that fails is reported, in a stable order, so the operator sees all the reasons at
 * once instead of fixing one and meeting the next.
 */
public final class ReservationEligibility {

    private ReservationEligibility() {
    }

    /**
     * @param limit how many active reservations a reader may have: the loan limit of the configuration
     * @throws BusinessRuleException with every reason when the reservation is not allowed
     */
    public static void check(ReservationSituation situation, int limit) {
        var reasons = refusals(situation, limit);
        if (!reasons.isEmpty()) {
            throw new BusinessRuleException("A reserva não pode ser feita: " + String.join("; ", reasons) + ".");
        }
    }

    /** The reasons the reservation is not allowed, in a stable order; empty when it is allowed. */
    public static List<String> refusals(ReservationSituation situation, int limit) {
        if (situation == null) {
            throw new IllegalArgumentException("the reservation situation is required");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("the reservation limit must be at least one");
        }
        var reasons = new ArrayList<String>();
        if (!situation.readerActive()) {
            reasons.add("o leitor está inativo");
        }
        if (situation.readerBlocked()) {
            reasons.add("o leitor está bloqueado");
        }
        if (situation.bookHasAvailableCopy()) {
            reasons.add("o livro tem exemplar disponível, então deve ser feito o empréstimo");
        }
        if (situation.readerHasBookOnLoan()) {
            reasons.add("o leitor já tem este livro emprestado");
        }
        if (situation.hasActiveReservationForBook()) {
            reasons.add("o leitor já tem uma reserva ativa deste livro");
        }
        if (situation.activeReservations() >= limit) {
            reasons.add("o leitor já atingiu o limite de " + limit
                    + (limit == 1 ? " reserva ativa" : " reservas ativas"));
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
