package com.riverfount.booksmanager.user.application.port.out;

/** Outbound port that makes the text of a reader access token: random, opaque and impossible to guess. */
public interface ReaderAccessTokenGenerator {

    String generate();
}
