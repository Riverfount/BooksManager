package com.riverfount.booksmanager.user.application.port.in;

/**
 * Creates the first administrator when the system has no user at all, so
 * that someone can log in and create the others (RF08). It never creates
 * a second one nor changes an existing user.
 */
public interface CreateInitialAdminUseCase {

    InitialAdminResult createInitialAdmin(CreateInitialAdminCommand command);
}
