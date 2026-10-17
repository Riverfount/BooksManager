package com.riverfount.booksmanager.user.application.port.in;

/**
 * What the loan module may know about a reader (RN11), without reaching the user module's
 * classes: the two facts of the reader's own state, and the verdict of the domain.
 *
 * @param active the reader is active
 * @param blocked the reader was blocked by an operator
 * @param canBorrow the reader may borrow as far as the user module is concerned ({@code Reader.canBorrow});
 *        the suspension and the loans are the loan module's own
 */
public record ReaderBorrowingState(Long readerId, boolean active, boolean blocked, boolean canBorrow) {
}
