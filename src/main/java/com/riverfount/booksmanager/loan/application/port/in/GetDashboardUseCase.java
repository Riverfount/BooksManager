package com.riverfount.booksmanager.loan.application.port.in;

/** Reads the numbers of the dashboard (RF23, RF25), in one call. */
public interface GetDashboardUseCase {

    Dashboard get();
}
