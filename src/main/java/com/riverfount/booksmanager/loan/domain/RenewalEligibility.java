package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules that decide whether the reader may renew a loan, by their own state (RN21), applied to the facts of a
 * {@link RenewalSituation}. A reader who is inactive, blocked, with a pending fine or suspended may not extend the
 * loans they already have, the same states that stop a new loan ({@link BorrowingEligibility}); it is one rule for the
 * desk and for the reader's own access. What belongs to the loan itself (closed, late, a waiting reservation, the
 * renewal limit) is {@link Loan#renew}'s. Every state that applies is reported, in a stable order, so the reader or the
 * operator sees all the reasons at once.
 */
public final class RenewalEligibility {

    private RenewalEligibility() {
    }

    /** @throws BusinessRuleException with every reason when the reader may not renew */
    public static void check(RenewalSituation situation) {
        var reasons = refusals(situation);
        if (!reasons.isEmpty()) {
            throw new BusinessRuleException("A renovação não pode ser feita: " + String.join("; ", reasons) + ".");
        }
    }

    /** The reasons the reader may not renew, in a stable order; empty when they may. */
    public static List<String> refusals(RenewalSituation situation) {
        if (situation == null) {
            throw new IllegalArgumentException("the renewal situation is required");
        }
        var reasons = new ArrayList<String>();
        if (!situation.readerActive()) {
            reasons.add("o leitor está inativo");
        }
        if (situation.readerBlocked()) {
            reasons.add("o leitor está bloqueado");
        }
        if (situation.hasPendingFine()) {
            reasons.add("o leitor tem multa pendente");
        }
        if (situation.hasSuspensionInForce()) {
            reasons.add("o leitor está suspenso");
        }
        return List.copyOf(reasons);
    }
}
