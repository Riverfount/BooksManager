package com.riverfount.booksmanager.loan.domain;

/** The two warnings a reader gets about a loan (RF28): it is about to fall due, or it is late. */
public enum LoanNoticeKind {
    DUE_SOON,
    OVERDUE
}
