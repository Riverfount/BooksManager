package com.riverfount.booksmanager.loan.domain;

/** Where a fine stands (RF21): waiting for the reader, paid by the reader, or waived by the library. */
public enum FineStatus {
    PENDING,
    PAID,
    WAIVED
}
