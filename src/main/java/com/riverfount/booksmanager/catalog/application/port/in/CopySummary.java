package com.riverfount.booksmanager.catalog.application.port.in;

/**
 * What the loan module's reports may know about a copy, without reaching the catalog's classes
 * (RF23): which copy it is (the asset code) and of which book (its title).
 */
public record CopySummary(Long copyId, String assetCode, Long bookId, String bookTitle) {
}
