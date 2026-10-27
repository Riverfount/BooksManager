package com.riverfount.booksmanager.loan.application.port.in;

/** The reader asks "how am I?": whether they can borrow, and what stops them (Phase 6, RN05, RN08, RN11, RN15). */
public interface GetOwnStandingUseCase {

    ReaderStanding get(GetOwnStandingQuery query);
}
