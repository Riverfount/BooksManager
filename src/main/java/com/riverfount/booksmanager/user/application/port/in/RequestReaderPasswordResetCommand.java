package com.riverfount.booksmanager.user.application.port.in;

/** "I forgot my password": the e-mail the reader is registered with. */
public record RequestReaderPasswordResetCommand(String email) {
}
