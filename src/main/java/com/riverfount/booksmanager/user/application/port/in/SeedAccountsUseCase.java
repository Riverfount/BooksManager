package com.riverfount.booksmanager.user.application.port.in;

/**
 * Creates, at startup, an operator and a reader with access, so every profile can be tried without going through
 * the invitation by e-mail. Both are idempotent and optional: they are created only when configured and when no
 * user has the login yet, and an existing user is never changed.
 */
public interface SeedAccountsUseCase {

    SeedResult seedOperator(SeedOperatorCommand command);

    SeedResult seedReader(SeedReaderCommand command);
}
