package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * What the loan module may know about a copy (RN03), without reaching the catalog's
 * classes: whether it exists is the absence of this answer, {@code lendable} tells
 * if it is active and available to be borrowed, and {@code reserved} tells if it is
 * being kept for a reservation (RF18), in which case it is not lendable to anyone else.
 * {@code bookActive} tells if the book of the copy is still active (RN14): inactivating a book does not touch its
 * copies, so a copy can be lendable while its book is not, and the loan module refuses the loan for that reason.
 */
public record CopyAvailability(Long copyId, boolean lendable, boolean reserved, boolean bookActive) {
}
