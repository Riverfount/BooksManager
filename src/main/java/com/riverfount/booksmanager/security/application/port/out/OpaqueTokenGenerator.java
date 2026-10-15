package com.riverfount.booksmanager.security.application.port.out;

/** Outbound port that generates the random, unguessable text of a refresh token. */
public interface OpaqueTokenGenerator {

    String generate();
}
