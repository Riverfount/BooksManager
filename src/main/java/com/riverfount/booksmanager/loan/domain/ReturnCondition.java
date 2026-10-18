package com.riverfount.booksmanager.loan.domain;

/**
 * The condition of a copy when it comes back (RF15). It is only recorded, with no charge:
 * a damaged or lost copy never goes back to the shelf by itself, and the late penalty
 * does not depend on it.
 */
public enum ReturnCondition {
    GOOD,
    DAMAGED,
    LOST
}
