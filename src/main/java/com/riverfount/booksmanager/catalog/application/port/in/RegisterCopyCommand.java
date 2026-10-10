package com.riverfount.booksmanager.catalog.application.port.in;

public record RegisterCopyCommand(Long bookId, String assetCode) {
}
