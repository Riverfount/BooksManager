package com.riverfount.booksmanager.loan.application.port.in;

/** Asks for the standing of a reader (Phase 6, RF10, RF20, RF21): the reader comes from the token, never the route. */
public record GetOwnStandingQuery(Long readerId) {
}
