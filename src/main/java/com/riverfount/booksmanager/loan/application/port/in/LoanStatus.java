package com.riverfount.booksmanager.loan.application.port.in;

/** The situation of a loan in the listing (RF14, HU30): not yet returned, or closed by the return. */
public enum LoanStatus {
    ACTIVE,
    RETURNED
}
