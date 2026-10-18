package com.riverfount.booksmanager.catalog.domain;

/**
 * The condition a borrowed copy comes back in, in the catalog's own words (RF15). The loan
 * module has its own type for the same fact and translates it, so the catalog never depends on
 * the loan.
 */
public enum CopyReturnCondition {
    GOOD,
    DAMAGED,
    LOST
}
