package com.riverfount.booksmanager.catalog.application.port.out;

/**
 * Outbound port of the catalog, in its own language: does this copy have an active loan? (RN10)
 * The catalog does not know the loan module. This is the dependency inversion that lets it ask
 * anyway without a cycle between the modules: the loan module provides the implementation (an
 * adapter that reads its loans) and the catalog depends only on this interface.
 */
public interface ActiveLoanChecker {

    /** Whether the copy is out on a loan that was not returned yet. */
    boolean hasActiveLoan(Long copyId);
}
