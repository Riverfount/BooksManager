package com.riverfount.booksmanager.loan.application.port.in;

/**
 * A line of the most borrowed books report (RF24): the book and how many loans its copies had in the period.
 */
public record MostBorrowedBook(Long bookId, String title, long totalLoans) {
}
