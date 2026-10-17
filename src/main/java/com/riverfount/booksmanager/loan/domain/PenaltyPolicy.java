package com.riverfount.booksmanager.loan.domain;

/**
 * What the library does with a loan returned late (RF20, RN08): charge a fine,
 * suspend the reader, both, or nothing. The library chooses it in its
 * configuration (RN16), so changing it is never a change of code.
 */
public enum PenaltyPolicy {
    FINE,
    SUSPENSION,
    BOTH,
    NONE
}
