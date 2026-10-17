package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.ActiveLoanChecker;
import java.util.HashSet;
import java.util.Set;

/**
 * Test stand-in for the loan module behind {@link ActiveLoanChecker}: no copy has an active loan
 * until a test says so with {@link #markActiveLoan(Long)}.
 */
class StubActiveLoanChecker implements ActiveLoanChecker {

    private final Set<Long> copiesWithActiveLoan = new HashSet<>();

    void markActiveLoan(Long copyId) {
        copiesWithActiveLoan.add(copyId);
    }

    void clearActiveLoan(Long copyId) {
        copiesWithActiveLoan.remove(copyId);
    }

    @Override
    public boolean hasActiveLoan(Long copyId) {
        return copiesWithActiveLoan.contains(copyId);
    }
}
