package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * What the loan module may know about a copy (RN03), without reaching the catalog's
 * classes: whether it exists is the absence of this answer, {@code lendable} tells
 * if it is active and available to be borrowed, and {@code reserved} tells if it is
 * being kept for a reservation (RF18), in which case it is not lendable to anyone else.
 */
public record CopyAvailability(Long copyId, boolean lendable, boolean reserved) {
}
