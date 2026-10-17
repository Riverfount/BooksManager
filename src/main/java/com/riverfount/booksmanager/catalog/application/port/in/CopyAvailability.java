package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * What the loan module may know about a copy (RN03), without reaching the catalog's
 * classes: whether it exists is the absence of this answer, and {@code lendable} tells
 * if it is active and available to be borrowed.
 */
public record CopyAvailability(Long copyId, boolean lendable) {
}
