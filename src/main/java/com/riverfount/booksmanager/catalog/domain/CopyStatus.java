package com.riverfount.booksmanager.catalog.domain;

/**
 * Status of a copy in the collection.
 */
public enum CopyStatus {
    AVAILABLE,
    BORROWED,
    RESERVED,
    DAMAGED,
    LOST
}
